package com.example.dam.metadata.application.enrichment;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetNotFoundException;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.common.domain.Actor;
import com.example.dam.metadata.application.AssetStatusService;
import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.application.MetadataWriter;
import com.example.dam.metadata.application.enrichment.EnrichmentOutcome.ProposalOutcome;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.MetadataStatus;
import com.example.dam.metadata.domain.audit.AuditAction;
import com.example.dam.metadata.domain.proposal.ApprovalDecision;
import com.example.dam.metadata.domain.proposal.ApprovalPolicy;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import com.example.dam.metadata.domain.proposal.MetadataProposal;
import com.example.dam.metadata.domain.proposal.MetadataProposalRepository;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.value.MetadataValue;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists an enrichment result atomically. Every candidate is re-evaluated by the deterministic
 * {@link ApprovalPolicy} against the latest stored metadata (state may have changed while the LLM was working),
 * so the AI layer can never bypass governance.
 */
@Component
public class EnrichmentResultRecorder {

    private final AssetRepository assetRepository;
    private final AssetMetadataRepository metadataRepository;
    private final MetadataProposalRepository proposalRepository;
    private final MetadataSchemaService schemaService;
    private final ApprovalPolicy approvalPolicy;
    private final MetadataWriter writer;
    private final AssetStatusService statusService;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    EnrichmentResultRecorder(AssetRepository assetRepository, AssetMetadataRepository metadataRepository,
                             MetadataProposalRepository proposalRepository, MetadataSchemaService schemaService,
                             ApprovalPolicy approvalPolicy, MetadataWriter writer, AssetStatusService statusService,
                             MeterRegistry meterRegistry, Clock clock) {
        this.assetRepository = assetRepository;
        this.metadataRepository = metadataRepository;
        this.proposalRepository = proposalRepository;
        this.schemaService = schemaService;
        this.approvalPolicy = approvalPolicy;
        this.writer = writer;
        this.statusService = statusService;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Transactional
    public EnrichmentOutcome record(MetadataEnrichmentResult result, Actor requestedBy) {
        Asset asset = assetRepository.lockById(result.assetId())
                .orElseThrow(() -> new AssetNotFoundException(result.assetId()));
        MetadataSchema schema = schemaService.schemaFor(asset.type());
        AssetMetadataSnapshot snapshot = AssetMetadataSnapshot.of(asset.id(), metadataRepository.findByAssetId(asset.id()));
        Instant now = clock.instant();

        List<ProposalOutcome> outcomes = new ArrayList<>();
        List<MetadataWriter.Change> changes = new ArrayList<>();
        for (PlannedProposal planned : result.proposals()) {
            MetadataCandidate candidate = planned.candidate();
            Optional<AssetMetadata> existing = snapshot.entry(candidate.fieldKey());
            ApprovalDecision decision = approvalPolicy.decide(candidate.fieldKey(), schema.field(candidate.fieldKey()),
                    existing, candidate.value(), candidate.confidence());
            if (!decision.isRecordable()) {
                outcomes.add(outcome(null, candidate, decision.valueToApply(), decision, null));
                continue;
            }
            if (decision.route() != ApprovalRoute.REJECTED_BY_POLICY) {
                // At most one open proposal per field: the latest run reflects the latest evidence.
                writer.supersedeOpenProposals(asset.id(), candidate.fieldKey(), MetadataWriter.APPROVAL_POLICY,
                        "Superseded by enrichment run " + result.workflowRunId());
            }
            MetadataProposal proposal = newProposal(result, asset, candidate, existing, decision, requestedBy, now);
            proposalRepository.insert(proposal);
            switch (decision.route()) {
                case AUTO_APPROVE -> changes.add(autoApply(asset, candidate, existing, decision, proposal, now));
                case REJECTED_BY_POLICY -> writer.recordProposalDecision(proposal, AuditAction.PROPOSAL_REJECTED,
                        decision.reason());
                default -> {
                    // HUMAN_REVIEW and LOW_CONFIDENCE stay open (PROPOSED) for a human decision
                }
            }
            outcomes.add(outcome(proposal.id(), candidate, proposal.proposedValue(), decision, proposal.status()));
        }
        writer.apply(asset, changes);
        AssetStatus status = statusService.refresh(asset);
        outcomes.forEach(outcome -> meterRegistry.counter("dam.metadata.proposals",
                "route", outcome.route().name(), "field", outcome.fieldKey()).increment());
        return new EnrichmentOutcome(result.workflowRunId(), asset.id(), status, outcomes, result.taxonomy(),
                result.qualityReview(), result.steps(), result.warnings(), result.aiModel());
    }

    private static MetadataProposal newProposal(MetadataEnrichmentResult result, Asset asset, MetadataCandidate candidate,
                                                Optional<AssetMetadata> existing, ApprovalDecision decision,
                                                Actor requestedBy, Instant now) {
        boolean decidedByPolicy = decision.route() == ApprovalRoute.AUTO_APPROVE
                || decision.route() == ApprovalRoute.REJECTED_BY_POLICY;
        MetadataStatus status = switch (decision.route()) {
            case AUTO_APPROVE -> MetadataStatus.APPROVED;
            case REJECTED_BY_POLICY -> MetadataStatus.REJECTED;
            default -> MetadataStatus.PROPOSED;
        };
        String comment = decidedByPolicy ? decision.reason() + " (enrichment requested by " + requestedBy + ")" : null;
        return new MetadataProposal(UUID.randomUUID(), asset.id(), candidate.fieldKey(), decision.valueToApply(),
                existing.map(AssetMetadata::value).orElse(null), candidate.confidence(), candidate.explanation(),
                candidate.evidence(), MetadataSource.AI_GENERATED, candidate.proposedBy(), candidate.aiModel(),
                result.workflowRunId(), decision.route(), decision.reason(), status, now,
                decidedByPolicy ? now : null, decidedByPolicy ? MetadataWriter.APPROVAL_POLICY.id() : null, comment,
                null);
    }

    private static MetadataWriter.Change autoApply(Asset asset, MetadataCandidate candidate,
                                                   Optional<AssetMetadata> existing, ApprovalDecision decision,
                                                   MetadataProposal proposal, Instant now) {
        Actor agent = Actor.ai(candidate.proposedBy());
        AssetMetadata next = existing
                .map(current -> current.replacedBy(decision.valueToApply(), MetadataSource.AI_GENERATED,
                        candidate.confidence(), MetadataStatus.VALIDATED, candidate.aiModel(), agent, now))
                .orElseGet(() -> AssetMetadata.create(asset.id(), candidate.fieldKey(), decision.valueToApply(),
                        MetadataSource.AI_GENERATED, candidate.confidence(), MetadataStatus.VALIDATED,
                        candidate.aiModel(), agent, now));
        return new MetadataWriter.Change(candidate.fieldKey(), existing.orElse(null), next,
                AuditAction.VALUE_AUTO_APPLIED, agent, MetadataWriter.APPROVAL_POLICY.id(), proposal.id(),
                decision.reason());
    }

    private static ProposalOutcome outcome(UUID proposalId, MetadataCandidate candidate, MetadataValue value,
                                           ApprovalDecision decision, MetadataStatus status) {
        return new ProposalOutcome(proposalId, candidate.fieldKey(), value, candidate.confidence(), decision.route(),
                status, decision.reason(), candidate.explanation(), candidate.evidence(), candidate.proposedBy());
    }
}

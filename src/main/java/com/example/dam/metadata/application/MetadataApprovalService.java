package com.example.dam.metadata.application;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetNotFoundException;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.common.domain.ActionNotPermittedException;
import com.example.dam.common.domain.Actor;
import com.example.dam.common.domain.BusinessRuleViolationException;
import com.example.dam.common.domain.ConflictException;
import com.example.dam.common.domain.InvalidRequestException;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.MetadataStatus;
import com.example.dam.metadata.domain.audit.AuditAction;
import com.example.dam.metadata.domain.proposal.MetadataProposal;
import com.example.dam.metadata.domain.proposal.MetadataProposalRepository;
import com.example.dam.metadata.domain.proposal.ProposalNotFoundException;
import com.example.dam.metadata.domain.proposal.ProposalQuery;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.validation.MetadataValidationException;
import com.example.dam.metadata.domain.validation.MetadataValueParser;
import com.example.dam.metadata.domain.validation.MetadataValueValidator;
import com.example.dam.metadata.domain.validation.ValidationIssue;
import com.example.dam.metadata.domain.value.MetadataValue;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human-in-the-loop decisions on AI proposals. Approval re-validates the value against the current schema,
 * refuses stale proposals and is always audited. AI agents and system components can never approve.
 */
@Service
public class MetadataApprovalService {

    private final MetadataProposalRepository proposalRepository;
    private final AssetRepository assetRepository;
    private final AssetMetadataRepository metadataRepository;
    private final MetadataSchemaService schemaService;
    private final MetadataValueValidator valueValidator;
    private final MetadataValueParser parser;
    private final MetadataWriter writer;
    private final AssetStatusService statusService;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public MetadataApprovalService(MetadataProposalRepository proposalRepository, AssetRepository assetRepository,
                                   AssetMetadataRepository metadataRepository, MetadataSchemaService schemaService,
                                   MetadataValueValidator valueValidator, MetadataValueParser parser,
                                   MetadataWriter writer, AssetStatusService statusService,
                                   MeterRegistry meterRegistry, Clock clock) {
        this.proposalRepository = proposalRepository;
        this.assetRepository = assetRepository;
        this.metadataRepository = metadataRepository;
        this.schemaService = schemaService;
        this.valueValidator = valueValidator;
        this.parser = parser;
        this.writer = writer;
        this.statusService = statusService;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MetadataProposal> list(ProposalQuery query) {
        return proposalRepository.find(query);
    }

    @Transactional(readOnly = true)
    public MetadataProposal get(UUID proposalId) {
        return proposalRepository.findById(proposalId).orElseThrow(() -> new ProposalNotFoundException(proposalId));
    }

    @Transactional
    public MetadataProposal approve(UUID proposalId, Actor reviewer, String comment) {
        return approve(proposalId, reviewer, comment, null);
    }

    /**
     * Approves a proposal, optionally with a value edited by the reviewer. An edited value is human-authored: it is
     * stored as {@code MANUAL} (authoritative) while the proposal keeps both the AI value and the applied value.
     *
     * @param editedRawValue reviewer's replacement value (same formats as manual edits), {@code null} to apply as is
     */
    @Transactional
    public MetadataProposal approve(UUID proposalId, Actor reviewer, String comment, Object editedRawValue) {
        requireHumanReviewer(reviewer);
        MetadataProposal proposal = get(proposalId);
        Asset asset = assetRepository.lockById(proposal.assetId())
                .orElseThrow(() -> new AssetNotFoundException(proposal.assetId()));
        if (!proposal.isOpen()) {
            throw new ConflictException("PROPOSAL_NOT_OPEN", "Proposal %s is already %s".formatted(proposalId, proposal.status()));
        }
        MetadataFieldDefinition field = schemaService.schemaFor(asset.type()).field(proposal.fieldKey())
                .orElseThrow(() -> new BusinessRuleViolationException("UNSUPPORTED_FIELD",
                        "Field '%s' is no longer defined in the metadata schema".formatted(proposal.fieldKey())));

        Optional<AssetMetadata> current = metadataRepository.findByAssetId(asset.id()).stream()
                .filter(entry -> entry.fieldKey().equals(proposal.fieldKey()))
                .findFirst();
        if (!sameValue(proposal.currentValue(), current.map(AssetMetadata::value).orElse(null))) {
            throw new ConflictException("STALE_PROPOSAL", ("Field '%s' changed after the proposal was created; "
                    + "re-run enrichment or set the value manually").formatted(proposal.fieldKey()));
        }
        Instant now = clock.instant();
        MetadataValue edited = editedRawValue == null ? null : parser.parse(field, editedRawValue).valueOrThrow();
        MetadataProposal approved = proposal.approve(reviewer, comment, now, edited);
        List<ValidationIssue> errors = valueValidator.validate(field, approved.effectiveValue()).stream()
                .filter(ValidationIssue::isError).toList();
        if (!errors.isEmpty()) {
            throw new MetadataValidationException(errors);
        }

        boolean editedByReviewer = approved.wasEditedByReviewer();
        MetadataSource source = editedByReviewer ? MetadataSource.MANUAL : proposal.source();
        Double confidence = editedByReviewer ? null : proposal.confidence();
        String aiModel = editedByReviewer ? null : proposal.aiModel();
        AssetMetadata next = current
                .map(existing -> existing.replacedBy(approved.effectiveValue(), source, confidence,
                        MetadataStatus.APPROVED, aiModel, reviewer, now))
                .orElseGet(() -> AssetMetadata.create(asset.id(), proposal.fieldKey(), approved.effectiveValue(),
                        source, confidence, MetadataStatus.APPROVED, aiModel, reviewer, now));
        proposalRepository.updateDecision(approved);
        String reason = editedByReviewer
                ? "Approved with reviewer edits" + (comment == null || comment.isBlank() ? "" : ": " + comment)
                : comment == null || comment.isBlank() ? "Approved by reviewer" : comment;
        writer.apply(asset, List.of(new MetadataWriter.Change(proposal.fieldKey(), current.orElse(null), next,
                AuditAction.PROPOSAL_APPROVED, reviewer, reviewer.id(), proposal.id(), reason)));
        statusService.refresh(asset);
        countDecision("approved", proposal);
        return approved;
    }

    @Transactional
    public MetadataProposal reject(UUID proposalId, Actor reviewer, String reason) {
        requireHumanReviewer(reviewer);
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("REJECTION_REASON_REQUIRED", "A reason is required to reject a proposal");
        }
        MetadataProposal proposal = get(proposalId);
        Asset asset = assetRepository.lockById(proposal.assetId())
                .orElseThrow(() -> new AssetNotFoundException(proposal.assetId()));
        MetadataProposal rejected = proposal.reject(reviewer, reason, clock.instant());
        proposalRepository.updateDecision(rejected);
        writer.recordProposalDecision(rejected, AuditAction.PROPOSAL_REJECTED, reason);
        statusService.refresh(asset);
        countDecision("rejected", proposal);
        return rejected;
    }

    private void countDecision(String decision, MetadataProposal proposal) {
        meterRegistry.counter("dam.metadata.approval.decisions", "decision", decision, "field", proposal.fieldKey(),
                "route", proposal.route().name()).increment();
    }

    public static void requireHumanReviewer(Actor reviewer) {
        if (!reviewer.isIdentifiedHuman()) {
            throw new ActionNotPermittedException("REVIEWER_REQUIRED",
                    "Approval decisions require an identified human reviewer (X-User-Id header)");
        }
    }

    private static boolean sameValue(MetadataValue expected, MetadataValue actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        return expected.isEquivalentTo(actual);
    }
}

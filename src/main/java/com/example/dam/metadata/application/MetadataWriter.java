package com.example.dam.metadata.application;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.common.domain.Actor;
import com.example.dam.common.observability.CorrelationContext;
import com.example.dam.metadata.application.port.MetadataAuditLog;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.audit.AuditAction;
import com.example.dam.metadata.domain.audit.MetadataAuditEvent;
import com.example.dam.metadata.domain.proposal.MetadataProposal;
import com.example.dam.metadata.domain.proposal.MetadataProposalRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single write path for metadata values and proposal decisions. Guarantees that every modification is
 * persisted together with its audit event, bumps the asset's metadata revision once per batch and publishes a
 * single {@link MetadataChangedEvent}. Callers must hold a transaction (and the asset row lock).
 */
@Component
public class MetadataWriter {

    public static final Actor APPROVAL_POLICY = Actor.system("approval-policy");

    private final AssetRepository assetRepository;
    private final AssetMetadataRepository metadataRepository;
    private final MetadataProposalRepository proposalRepository;
    private final MetadataAuditLog auditLog;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    MetadataWriter(AssetRepository assetRepository, AssetMetadataRepository metadataRepository,
                   MetadataProposalRepository proposalRepository, MetadataAuditLog auditLog,
                   ApplicationEventPublisher events, Clock clock) {
        this.assetRepository = assetRepository;
        this.metadataRepository = metadataRepository;
        this.proposalRepository = proposalRepository;
        this.auditLog = auditLog;
        this.events = events;
        this.clock = clock;
    }

    /**
     * One value change.
     *
     * @param previous   value before the change, {@code null} when the field was empty
     * @param next       new value, {@code null} to remove the field
     * @param approvedBy approver for approved/auto-applied changes
     */
    public record Change(String fieldKey, AssetMetadata previous, AssetMetadata next, AuditAction action,
                         Actor actor, String approvedBy, UUID proposalId, String reason) {
        public Change {
            Objects.requireNonNull(fieldKey, "fieldKey");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(actor, "actor");
            if (previous == null && next == null) {
                throw new IllegalArgumentException("A change needs a previous or a next value");
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Asset asset, List<Change> changes) {
        if (changes.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        Set<String> fields = new LinkedHashSet<>();
        for (Change change : changes) {
            if (change.next() != null) {
                metadataRepository.upsert(change.next());
            } else {
                metadataRepository.delete(asset.id(), change.fieldKey());
            }
            fields.add(change.fieldKey());
            AssetMetadata source = change.next() != null ? change.next() : change.previous();
            auditLog.append(new MetadataAuditEvent(UUID.randomUUID(), asset.id(), change.fieldKey(),
                    change.action(),
                    change.previous() == null ? null : change.previous().value(),
                    change.next() == null ? null : change.next().value(),
                    source.source(), source.aiModel(), source.confidence(), change.actor().id(),
                    change.approvedBy(), change.proposalId(), change.reason(), correlationId(), now));
        }
        long revision = assetRepository.incrementMetadataRevision(asset.id(), now);
        events.publishEvent(new MetadataChangedEvent(asset.id(), fields, revision));
    }

    /** Records a proposal decision that does not change metadata (rejection, supersession). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordProposalDecision(MetadataProposal proposal, AuditAction action, String reason) {
        auditLog.append(new MetadataAuditEvent(UUID.randomUUID(), proposal.assetId(), proposal.fieldKey(), action,
                proposal.currentValue(), proposal.proposedValue(), proposal.source(), proposal.aiModel(),
                proposal.confidence(), proposal.decidedBy() == null ? APPROVAL_POLICY.id() : proposal.decidedBy(),
                null, proposal.id(), reason, correlationId(), clock.instant()));
    }

    /** Closes open proposals for a field because newer information replaces them. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void supersedeOpenProposals(UUID assetId, String fieldKey, Actor actor, String reason) {
        for (MetadataProposal open : proposalRepository.findOpenByAssetAndField(assetId, fieldKey)) {
            MetadataProposal superseded = open.reject(actor, reason, clock.instant());
            proposalRepository.updateDecision(superseded);
            recordProposalDecision(superseded, AuditAction.PROPOSAL_SUPERSEDED, reason);
        }
    }

    private static String correlationId() {
        return CorrelationContext.correlationId().orElse(null);
    }
}

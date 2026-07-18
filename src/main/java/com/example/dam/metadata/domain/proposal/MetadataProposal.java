package com.example.dam.metadata.domain.proposal;

import com.example.dam.common.domain.Actor;
import com.example.dam.common.domain.ConflictException;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.MetadataStatus;
import com.example.dam.metadata.domain.value.MetadataValue;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A machine-generated suggestion for one field, with confidence, explanation and evidence. Proposals never change
 * authoritative metadata by themselves; applying them is a separate, audited decision.
 *
 * @param currentValue value of the field when the proposal was created (stale-detection on approval)
 * @param appliedValue value applied on approval when the reviewer edited the proposed value, otherwise {@code null}
 */
public record MetadataProposal(
        UUID id,
        UUID assetId,
        String fieldKey,
        MetadataValue proposedValue,
        MetadataValue currentValue,
        double confidence,
        String explanation,
        List<String> evidence,
        MetadataSource source,
        String proposedBy,
        String aiModel,
        UUID workflowRunId,
        ApprovalRoute route,
        String routeReason,
        MetadataStatus status,
        Instant createdAt,
        Instant decidedAt,
        String decidedBy,
        String decisionComment,
        MetadataValue appliedValue) {

    public MetadataProposal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(fieldKey, "fieldKey");
        Objects.requireNonNull(proposedValue, "proposedValue");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be within [0,1]");
        }
        if (route == ApprovalRoute.NO_CHANGE) {
            throw new IllegalArgumentException("NO_CHANGE proposals are not recorded");
        }
        if (status == MetadataStatus.VALIDATED) {
            throw new IllegalArgumentException("Proposals are PROPOSED, APPROVED or REJECTED");
        }
        if (appliedValue != null && status != MetadataStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved proposals can carry an applied value");
        }
    }

    public boolean isOpen() {
        return status == MetadataStatus.PROPOSED;
    }

    public MetadataProposal approve(Actor actor, String comment, Instant now) {
        return approve(actor, comment, now, null);
    }

    /**
     * @param editedValue value the reviewer applied instead of the proposed one; {@code null} applies the proposal
     */
    public MetadataProposal approve(Actor actor, String comment, Instant now, MetadataValue editedValue) {
        requireOpen();
        MetadataValue applied = editedValue == null || editedValue.isEquivalentTo(proposedValue) ? null : editedValue;
        return decide(MetadataStatus.APPROVED, actor, comment, now, applied);
    }

    public MetadataProposal reject(Actor actor, String reason, Instant now) {
        requireOpen();
        return decide(MetadataStatus.REJECTED, actor, reason, now, null);
    }

    /** The value that approval applies: the reviewer's edit if any, otherwise the proposed value. */
    public MetadataValue effectiveValue() {
        return appliedValue != null ? appliedValue : proposedValue;
    }

    public boolean wasEditedByReviewer() {
        return appliedValue != null;
    }

    private MetadataProposal decide(MetadataStatus newStatus, Actor actor, String comment, Instant now,
                                    MetadataValue applied) {
        return new MetadataProposal(id, assetId, fieldKey, proposedValue, currentValue, confidence, explanation,
                evidence, source, proposedBy, aiModel, workflowRunId, route, routeReason, newStatus, createdAt, now,
                actor.id(), comment, applied);
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw new ConflictException("PROPOSAL_NOT_OPEN",
                    "Proposal %s is already %s".formatted(id, status));
        }
    }
}

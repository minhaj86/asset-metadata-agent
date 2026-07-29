package com.example.dam.metadata.domain.audit;

import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.value.MetadataValue;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of one metadata change or approval decision.
 *
 * @param actor      who performed the action (user id, {@code system:...} or {@code ai:...})
 * @param approvedBy human (or policy) that approved the change, when the change was approved
 * @param aiModel    model that produced the value, for AI-originated changes
 */
public record MetadataAuditEvent(
        UUID id,
        UUID assetId,
        String field,
        AuditAction action,
        MetadataValue previousValue,
        MetadataValue newValue,
        MetadataSource source,
        String aiModel,
        Double confidence,
        String actor,
        String approvedBy,
        UUID proposalId,
        String reason,
        String correlationId,
        Instant timestamp) {

    public MetadataAuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}

package com.example.dam.metadata.domain;

import com.example.dam.common.domain.Actor;
import com.example.dam.metadata.domain.value.MetadataValue;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Current value of one metadata field of an asset, including provenance. AI-generated values remain
 * distinguishable from authoritative ones through {@link #source()} and {@link #status()}.
 *
 * @param confidence model confidence for machine-generated values, {@code null} for authoritative values
 * @param aiModel    model that produced the value, if any
 */
public record AssetMetadata(
        UUID assetId,
        String fieldKey,
        MetadataValue value,
        MetadataSource source,
        Double confidence,
        MetadataStatus status,
        String aiModel,
        Instant createdAt,
        Instant updatedAt,
        String updatedBy) {

    public AssetMetadata {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(fieldKey, "fieldKey");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
        if (status != MetadataStatus.APPROVED && status != MetadataStatus.VALIDATED) {
            throw new IllegalArgumentException("Stored metadata must be APPROVED or VALIDATED, was " + status);
        }
        if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
            throw new IllegalArgumentException("confidence must be within [0,1]");
        }
    }

    public static AssetMetadata create(UUID assetId, String fieldKey, MetadataValue value, MetadataSource source,
                                       Double confidence, MetadataStatus status, String aiModel, Actor actor,
                                       Instant now) {
        return new AssetMetadata(assetId, fieldKey, value, source, confidence, status, aiModel, now, now, actor.id());
    }

    /** Returns the successor entry, keeping the original creation timestamp. */
    public AssetMetadata replacedBy(MetadataValue newValue, MetadataSource newSource, Double newConfidence,
                                    MetadataStatus newStatus, String newAiModel, Actor actor, Instant now) {
        return new AssetMetadata(assetId, fieldKey, newValue, newSource, newConfidence, newStatus, newAiModel,
                createdAt, now, actor.id());
    }

    /** Authoritative values are human-confirmed or come from a system of record; AI must never overwrite them. */
    public boolean isAuthoritative() {
        return status == MetadataStatus.APPROVED;
    }
}

package com.example.dam.metadata.application.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Derived, denormalised metadata summary of one asset (read model for lists, filters and dashboards). It is
 * recomputed deterministically whenever the asset's metadata or open review work changes and is never a source of
 * truth.
 *
 * @param usageRightsEnd end of the usage rights, taken from the field of the schema's rights-expiry rule
 */
public record AssetMetadataSummary(
        UUID assetId,
        int schemaVersion,
        boolean valid,
        double completeness,
        double requiredCompleteness,
        int qualityScore,
        List<String> missingRequired,
        int errorCount,
        int warningCount,
        int aiValueCount,
        int pendingReviewCount,
        int lowConfidenceCount,
        LocalDate usageRightsEnd,
        Instant refreshedAt) {

    public AssetMetadataSummary {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(refreshedAt, "refreshedAt");
        missingRequired = List.copyOf(missingRequired);
    }
}

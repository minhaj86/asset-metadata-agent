package com.example.dam.metadata.application.catalog;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.asset.domain.AssetType;
import java.util.List;
import java.util.Objects;

/**
 * Catalog listing criteria; {@code null} attributes are ignored.
 *
 * @param text           matches the external asset id, the filename or any metadata value (case-insensitive)
 * @param filters        metadata field filters (equality, "contains" for repeatable fields), searchable fields only
 * @param taxonomyPath   assets classified at this taxonomy node or below
 * @param missingField   assets lacking this required field
 */
public record CatalogQuery(
        String text,
        AssetType type,
        AssetStatus status,
        AiStatus aiStatus,
        RightsStatus rightsStatus,
        Boolean missingRequired,
        String missingField,
        Boolean aiEnriched,
        Double completenessMin,
        Double completenessMax,
        Integer qualityMin,
        String taxonomyPath,
        List<FieldFilter> filters,
        CatalogSort sort,
        boolean descending,
        int page,
        int size) {

    public CatalogQuery {
        text = text == null || text.isBlank() ? null : text.strip();
        missingField = missingField == null || missingField.isBlank() ? null : missingField.strip();
        taxonomyPath = taxonomyPath == null || taxonomyPath.isBlank() ? null : taxonomyPath.strip();
        filters = filters == null ? List.of() : List.copyOf(filters);
        sort = sort == null ? CatalogSort.of(CatalogSort.Column.UPDATED_AT) : sort;
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("page must be >= 0 and size within 1..200");
        }
    }

    public record FieldFilter(String field, String value) {
        public FieldFilter {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(value, "value");
        }
    }

    /** Derived AI state of an asset, most actionable first. */
    public enum AiStatus { NOT_ENRICHED, QUEUED, RUNNING, REVIEW_PENDING, FAILED, ENRICHED }

    /** State of the usage rights relative to today and the schema's expiry warning window. */
    public enum RightsStatus { VALID, EXPIRING, EXPIRED, MISSING }
}

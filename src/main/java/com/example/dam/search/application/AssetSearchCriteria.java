package com.example.dam.search.application;

import com.example.dam.asset.domain.AssetType;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Structured search request executed deterministically. Combines metadata filters with optional vector
 * similarity (hybrid search).
 *
 * @param filters            field equals value (case-insensitive; "contains" for repeatable fields)
 * @param semanticQuery      free text ranked by embedding similarity
 * @param rightsExpiryAfter  usage rights end on/after this date
 * @param rightsExpiryBefore usage rights end on/before this date
 * @param missingFields      assets lacking all of these fields
 * @param similarToAssetId   rank by similarity to this asset's embedding
 */
public record AssetSearchCriteria(
        List<MetadataFilter> filters,
        String semanticQuery,
        LocalDate rightsExpiryAfter,
        LocalDate rightsExpiryBefore,
        Set<String> missingFields,
        AssetType assetType,
        UUID similarToAssetId,
        Integer limit) {

    public AssetSearchCriteria {
        filters = filters == null ? List.of() : List.copyOf(filters);
        missingFields = missingFields == null ? Set.of() : Set.copyOf(missingFields);
        semanticQuery = semanticQuery == null || semanticQuery.isBlank() ? null : semanticQuery.strip();
    }

    public record MetadataFilter(String field, String value) {
        public MetadataFilter {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(value, "value");
        }
    }

    public boolean hasRightsWindow() {
        return rightsExpiryAfter != null || rightsExpiryBefore != null;
    }

    public boolean isSemantic() {
        return semanticQuery != null || similarToAssetId != null;
    }
}

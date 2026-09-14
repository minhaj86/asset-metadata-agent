package com.example.dam.search.api;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.search.application.AssetSearchCriteria;
import com.example.dam.search.application.AssetSearchService.AssetSearchHit;
import com.example.dam.search.application.AssetSearchService.AssetSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** DTOs of the search API. */
final class SearchDtos {

    private SearchDtos() {
    }

    @Schema(description = "Structured (+ optional semantic) search criteria")
    record AssetSearchRequest(
            @Schema(description = "field -> value; equality (case-insensitive), contains for repeatable fields",
                    example = "{\"productType\": \"SHIRT\", \"color\": \"BLUE\"}") Map<String, String> filters,
            @Schema(description = "Free text ranked by vector similarity", example = "casual summer menswear")
            String semanticQuery,
            LocalDate rightsExpiryAfter,
            LocalDate rightsExpiryBefore,
            Set<String> missingFields,
            AssetType assetType,
            UUID similarToAssetId,
            Integer limit) {

        AssetSearchCriteria toCriteria() {
            List<AssetSearchCriteria.MetadataFilter> criteriaFilters = filters == null ? List.of()
                    : filters.entrySet().stream()
                    .map(entry -> new AssetSearchCriteria.MetadataFilter(entry.getKey(), entry.getValue()))
                    .toList();
            return new AssetSearchCriteria(criteriaFilters, semanticQuery, rightsExpiryAfter, rightsExpiryBefore,
                    missingFields, assetType, similarToAssetId, limit);
        }
    }

    record CriteriaResponse(Map<String, String> filters, String semanticQuery, LocalDate rightsExpiryAfter,
                            LocalDate rightsExpiryBefore, Set<String> missingFields, AssetType assetType,
                            UUID similarToAssetId, Integer limit) {
        static CriteriaResponse from(AssetSearchCriteria criteria) {
            Map<String, String> filters = new LinkedHashMap<>();
            criteria.filters().forEach(filter -> filters.put(filter.field(), filter.value()));
            return new CriteriaResponse(filters, criteria.semanticQuery(), criteria.rightsExpiryAfter(),
                    criteria.rightsExpiryBefore(), criteria.missingFields(), criteria.assetType(),
                    criteria.similarToAssetId(), criteria.limit());
        }
    }

    record AssetSearchHitResponse(UUID id, String externalAssetId, String filename, String mimeType, String assetUrl,
                                  AssetType type, AssetStatus status, Double similarity, Map<String, Object> metadata) {
        static AssetSearchHitResponse from(AssetSearchHit hit) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            hit.metadata().values().forEach((key, value) -> metadata.put(key, value.toPlainValue()));
            return new AssetSearchHitResponse(hit.asset().id(), hit.asset().externalAssetId(), hit.asset().filename(),
                    hit.asset().mimeType(), hit.asset().assetUrl().toString(), hit.asset().type(), hit.asset().status(),
                    hit.similarity() == null ? null : Math.round(hit.similarity() * 1000) / 1000.0, metadata);
        }
    }

    record AssetSearchResponse(CriteriaResponse criteria, boolean semanticApplied, boolean degraded,
                               List<String> warnings, int count, List<AssetSearchHitResponse> results) {
        static AssetSearchResponse from(AssetSearchResult result) {
            return from(result, result.warnings());
        }

        static AssetSearchResponse from(AssetSearchResult result, List<String> warnings) {
            List<AssetSearchHitResponse> hits = result.hits().stream().map(AssetSearchHitResponse::from).toList();
            return new AssetSearchResponse(CriteriaResponse.from(result.criteria()), result.semanticApplied(),
                    result.degraded(), warnings, hits.size(), hits);
        }
    }
}

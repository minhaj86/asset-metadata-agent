package com.example.dam.search.application.port;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.search.application.AssetSearchCriteria.MetadataFilter;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Executes hybrid (structured + vector) queries; the SQL lives in the infrastructure adapter. */
public interface AssetSearchRepository {

    List<SearchRow> search(SearchQuery query);

    /**
     * @param queryVector   {@code null} for purely structured search
     * @param minSimilarity applied only when {@code queryVector} is present; {@code null} disables the threshold
     */
    record SearchQuery(List<MetadataFilter> filters, Set<String> missingFields, AssetType assetType,
                       String rightsExpiryField, LocalDate rightsExpiryAfter, LocalDate rightsExpiryBefore,
                       float[] queryVector, UUID excludeAssetId, Double minSimilarity, int limit) {
    }

    record SearchRow(UUID assetId, Double similarity) {
    }
}

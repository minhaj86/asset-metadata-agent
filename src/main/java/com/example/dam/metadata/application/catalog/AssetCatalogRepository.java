package com.example.dam.metadata.application.catalog;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Query port of the asset catalog (assets joined with metadata summaries and enrichment runs). */
public interface AssetCatalogRepository {

    CatalogItem.Page search(CatalogQuery query, Context context);

    Optional<CatalogItem> findById(UUID assetId, Context context);

    /**
     * Values needed to derive catalog attributes deterministically.
     *
     * @param today              reference date for rights status
     * @param expiringBefore     rights ending on or before this date (and not expired) are EXPIRING
     * @param keyFields          metadata fields returned as key metadata
     * @param taxonomyField      field holding the taxonomy path
     */
    record Context(LocalDate today, LocalDate expiringBefore, List<String> keyFields, String taxonomyField) {
        public Context {
            keyFields = List.copyOf(keyFields);
        }
    }
}

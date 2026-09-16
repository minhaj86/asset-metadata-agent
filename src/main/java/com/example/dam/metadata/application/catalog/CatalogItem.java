package com.example.dam.metadata.application.catalog;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.catalog.CatalogQuery.AiStatus;
import com.example.dam.metadata.application.catalog.CatalogQuery.RightsStatus;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One row of the asset catalog: the asset reference, its key metadata values and the derived summary.
 *
 * @param keyMetadata configured key fields (plain values) for compact display
 * @param summary     {@code null} if the summary has not been computed yet
 * @param lastRun     latest enrichment run, {@code null} if the asset was never enriched
 */
public record CatalogItem(
        UUID id,
        String externalAssetId,
        String filename,
        String mimeType,
        String assetUrl,
        AssetType type,
        AssetStatus status,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> keyMetadata,
        AssetMetadataSummary summary,
        AiStatus aiStatus,
        RightsStatus rightsStatus,
        LastRun lastRun) {

    public record LastRun(UUID workflowId, RunStatus status, Instant createdAt, Instant finishedAt) {
    }

    public record Page(List<CatalogItem> items, int page, int size, long totalItems) {
        public Page {
            items = List.copyOf(items);
        }

        public int totalPages() {
            return (int) Math.ceil(totalItems / (double) size);
        }
    }
}

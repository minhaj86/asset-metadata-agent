package com.example.dam.metadata.api;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.catalog.AssetMetadataSummary;
import com.example.dam.metadata.application.catalog.CatalogItem;
import com.example.dam.metadata.application.catalog.CatalogQuery.AiStatus;
import com.example.dam.metadata.application.catalog.CatalogQuery.RightsStatus;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Asset with key metadata and its derived, deterministic metadata summary")
public record CatalogItemResponse(
        UUID id,
        String externalAssetId,
        String filename,
        String mimeType,
        String assetUrl,
        AssetType type,
        AssetStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Schema(description = "Configured key fields (dam.catalog.key-fields) as plain values") Map<String, Object> keyMetadata,
        MetadataSummaryResponse summary,
        AiStatus aiStatus,
        RightsStatus rightsStatus,
        LastRunResponse lastEnrichment) {

    public record MetadataSummaryResponse(boolean valid, double completeness, double requiredCompleteness, int qualityScore,
                                  List<String> missingRequiredFields, int errorCount, int warningCount,
                                  int aiValueCount, int pendingReviewCount, int lowConfidenceCount,
                                  LocalDate usageRightsEnd, Instant refreshedAt) {
        static MetadataSummaryResponse from(AssetMetadataSummary summary) {
            return summary == null ? null : new MetadataSummaryResponse(summary.valid(), summary.completeness(),
                    summary.requiredCompleteness(), summary.qualityScore(), summary.missingRequired(),
                    summary.errorCount(), summary.warningCount(), summary.aiValueCount(), summary.pendingReviewCount(),
                    summary.lowConfidenceCount(), summary.usageRightsEnd(), summary.refreshedAt());
        }
    }

    public record LastRunResponse(UUID workflowId, RunStatus status, Instant createdAt, Instant finishedAt) {
    }

    public record CatalogPageResponse(List<CatalogItemResponse> items, int page, int size, long totalItems, int totalPages) {
        static CatalogPageResponse from(CatalogItem.Page page) {
            return new CatalogPageResponse(page.items().stream().map(CatalogItemResponse::from).toList(), page.page(),
                    page.size(), page.totalItems(), page.totalPages());
        }
    }

    static CatalogItemResponse from(CatalogItem item) {
        return new CatalogItemResponse(item.id(), item.externalAssetId(), item.filename(), item.mimeType(),
                item.assetUrl(), item.type(), item.status(), item.createdAt(), item.updatedAt(), item.keyMetadata(),
                MetadataSummaryResponse.from(item.summary()), item.aiStatus(), item.rightsStatus(),
                item.lastRun() == null ? null : new LastRunResponse(item.lastRun().workflowId(),
                        item.lastRun().status(), item.lastRun().createdAt(), item.lastRun().finishedAt()));
    }
}

package com.example.dam.metadata.api;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.common.domain.InvalidRequestException;
import com.example.dam.metadata.application.catalog.AssetCatalogService;
import com.example.dam.metadata.application.catalog.CatalogQuery;
import com.example.dam.metadata.application.catalog.CatalogQuery.AiStatus;
import com.example.dam.metadata.application.catalog.CatalogQuery.FieldFilter;
import com.example.dam.metadata.application.catalog.CatalogQuery.RightsStatus;
import com.example.dam.metadata.application.catalog.CatalogSort;
import com.example.dam.metadata.application.catalog.TaxonomyUsageService;
import com.example.dam.metadata.application.catalog.TaxonomyUsageService.NodeUsage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
@Validated
@Tag(name = "Catalog", description = "Paged asset listing with derived metadata summaries (read model)")
class CatalogController {

    private final AssetCatalogService catalogService;
    private final TaxonomyUsageService taxonomyUsageService;

    CatalogController(AssetCatalogService catalogService, TaxonomyUsageService taxonomyUsageService) {
        this.catalogService = catalogService;
        this.taxonomyUsageService = taxonomyUsageService;
    }

    @GetMapping("/assets")
    @Operation(summary = "List assets with filters, sorting and pagination")
    CatalogItemResponse.CatalogPageResponse assets(
            @Parameter(description = "Matches external id, filename or any metadata value") @RequestParam(required = false) String q,
            @RequestParam(required = false) AssetType type,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) AiStatus aiStatus,
            @RequestParam(required = false) RightsStatus rightsStatus,
            @RequestParam(required = false) Boolean missingRequired,
            @RequestParam(required = false) String missingField,
            @RequestParam(required = false) Boolean aiEnriched,
            @RequestParam(required = false) @Min(0) @Max(100) Double completenessMin,
            @RequestParam(required = false) @Min(0) @Max(100) Double completenessMax,
            @RequestParam(required = false) @Min(0) @Max(100) Integer qualityMin,
            @RequestParam(required = false) String taxonomyPath,
            @Parameter(description = "Metadata filter field:value (repeatable), e.g. color:BLUE")
            @RequestParam(name = "filter", required = false) List<String> filters,
            @Parameter(description = "updatedAt, createdAt, externalAssetId, filename, type, status, completeness, "
                    + "qualityScore, missingRequired, rightsEnd or a key metadata field such as productId")
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(200) int size) {
        CatalogQuery query = new CatalogQuery(q, type, status, aiStatus, rightsStatus, missingRequired, missingField,
                aiEnriched, completenessMin, completenessMax, qualityMin, taxonomyPath, filters(filters), sort(sort),
                descending(direction), page, size);
        return CatalogItemResponse.CatalogPageResponse.from(catalogService.search(query));
    }

    @GetMapping("/assets/{assetId}")
    @Operation(summary = "One catalog row: key metadata, summary, AI and rights status")
    CatalogItemResponse asset(@PathVariable UUID assetId) {
        return CatalogItemResponse.from(catalogService.get(assetId));
    }

    @GetMapping("/key-fields")
    @Operation(summary = "Metadata fields returned as key metadata (sortable)")
    List<String> keyFields() {
        return catalogService.keyFields();
    }

    @GetMapping("/taxonomy-usage")
    @Operation(summary = "Number of assets per taxonomy node (exact and including descendants)")
    List<NodeUsage> taxonomyUsage() {
        return taxonomyUsageService.usage();
    }

    private static List<FieldFilter> filters(List<String> filters) {
        if (filters == null) {
            return List.of();
        }
        return filters.stream().filter(filter -> !filter.isBlank()).map(filter -> {
            int separator = filter.indexOf(':');
            if (separator < 1 || separator == filter.length() - 1) {
                throw new InvalidRequestException("INVALID_FILTER", "Expected field:value but got '%s'".formatted(filter));
            }
            return new FieldFilter(filter.substring(0, separator).strip(), filter.substring(separator + 1).strip());
        }).toList();
    }

    private static CatalogSort sort(String sort) {
        String normalized = sort.strip().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        return Arrays.stream(CatalogSort.Column.values())
                .filter(column -> column != CatalogSort.Column.METADATA_FIELD && column.name().equals(normalized))
                .findFirst()
                .map(CatalogSort::of)
                .orElseGet(() -> CatalogSort.byField(sort.strip()));
    }

    private static boolean descending(String direction) {
        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "desc" -> true;
            case "asc" -> false;
            default -> throw new InvalidRequestException("INVALID_SORT", "direction must be asc or desc");
        };
    }
}

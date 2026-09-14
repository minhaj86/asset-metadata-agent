package com.example.dam.search.api;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.search.api.SearchDtos.AssetSearchRequest;
import com.example.dam.search.api.SearchDtos.AssetSearchResponse;
import com.example.dam.search.api.SearchDtos.CriteriaResponse;
import com.example.dam.search.application.AssetSearchService;
import com.example.dam.search.application.DuplicateDetectionService;
import com.example.dam.search.application.DuplicateDetectionService.MatchKind;
import com.example.dam.search.application.MetadataRecommendationService;
import com.example.dam.search.application.NaturalLanguageSearchService;
import com.example.dam.search.application.NaturalLanguageSearchService.NaturalLanguageSearchResult;
import com.example.dam.search.application.NaturalLanguageSearchService.Refinements;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
@Tag(name = "Search", description = "Structured, hybrid and natural-language asset search")
class AssetSearchController {

    private final AssetSearchService searchService;
    private final NaturalLanguageSearchService naturalLanguageSearchService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final MetadataRecommendationService recommendationService;

    AssetSearchController(AssetSearchService searchService, NaturalLanguageSearchService naturalLanguageSearchService,
                          DuplicateDetectionService duplicateDetectionService,
                          MetadataRecommendationService recommendationService) {
        this.searchService = searchService;
        this.naturalLanguageSearchService = naturalLanguageSearchService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.recommendationService = recommendationService;
    }

    @PostMapping("/search")
    @Operation(summary = "Deterministic search: metadata filters, rights window, missing fields, optional vector ranking")
    AssetSearchResponse search(@RequestBody AssetSearchRequest request) {
        return AssetSearchResponse.from(searchService.search(request.toCriteria()));
    }

    @PostMapping("/semantic-search")
    @Operation(summary = "Natural-language search: the SearchIntentAgent interprets, Java validates and executes")
    NaturalLanguageSearchResponse semanticSearch(@Valid @RequestBody NaturalLanguageSearchRequest request) {
        NaturalLanguageSearchResult result = naturalLanguageSearchService.search(request.query(),
                new Refinements(request.filters(), request.assetType(), request.rightsExpiryBefore()), request.limit());
        return new NaturalLanguageSearchResponse(result.query(), result.interpretedByAi(),
                result.interpretation().interpretationNotes(), CriteriaResponse.from(result.result().criteria()),
                AssetSearchResponse.from(result.result(), result.warnings()));
    }

    @GetMapping("/{assetId}/duplicates")
    @Operation(summary = "Probable duplicates and similar assets (embedding similarity + identical key fields)")
    List<SimilarAssetResponse> duplicates(@PathVariable UUID assetId) {
        return duplicateDetectionService.findSimilar(assetId).stream()
                .map(match -> new SimilarAssetResponse(match.asset().id(), match.asset().externalAssetId(),
                        match.asset().filename(), Math.round(match.similarity() * 1000) / 1000.0, match.kind(),
                        match.identicalFields()))
                .toList();
    }

    @GetMapping("/{assetId}/recommendations")
    @Operation(summary = "Values shared by similar assets for fields this asset is missing (read-only hints)")
    List<RecommendationResponse> recommendations(@PathVariable UUID assetId) {
        return recommendationService.recommend(assetId).stream()
                .map(recommendation -> new RecommendationResponse(recommendation.field(),
                        recommendation.value().toPlainValue(), recommendation.confidence(),
                        recommendation.supportingAssets(), recommendation.supportingExternalAssetIds(),
                        recommendation.requiresHumanApproval()))
                .toList();
    }

    record NaturalLanguageSearchRequest(
            @NotBlank @Size(max = 500) @Schema(example = "front-facing blue men's shirts") String query,
            @Min(1) @Max(100) Integer limit,
            @Schema(description = "Explicit structured filters merged into the interpreted criteria (explicit wins)")
            Map<String, String> filters,
            @Schema(description = "Explicit asset type; overrides the interpreted one") AssetType assetType,
            @Schema(description = "Usage rights ending on or before this date") LocalDate rightsExpiryBefore) {
    }

    record NaturalLanguageSearchResponse(String query, boolean interpretedByAi, String interpretationNotes,
                                         CriteriaResponse interpretedCriteria, AssetSearchResponse search) {
    }

    record SimilarAssetResponse(UUID id, String externalAssetId, String filename, double similarity, MatchKind kind,
                                List<String> identicalFields) {
    }

    record RecommendationResponse(String field, Object value, double confidence, int supportingAssets,
                                  List<String> supportingExternalAssetIds, boolean requiresHumanApproval) {
    }
}

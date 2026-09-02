package com.example.dam.ai.tool;

import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.asset.application.AssetService;
import com.example.dam.asset.domain.Asset;
import com.example.dam.search.application.AssetSearchCriteria;
import com.example.dam.search.application.AssetSearchCriteria.MetadataFilter;
import com.example.dam.search.application.AssetSearchService;
import com.example.dam.search.application.AssetSearchService.AssetSearchHit;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Read-only asset tools. Delegates to application services; contains no business or persistence logic. */
@Component
@ConditionalOnAiEnabled
public class AssetTools {

    private static final int MAX_RESULTS = 5;

    private final AssetService assetService;
    private final AssetSearchService searchService;

    public AssetTools(AssetService assetService, AssetSearchService searchService) {
        this.assetService = assetService;
        this.searchService = searchService;
    }

    public record AssetSummary(String id, String externalAssetId, String filename, String type, String status,
                               Map<String, Object> metadata) {
    }

    @Tool("Returns an asset reference (filename, type, status) by internal UUID or external asset id such as DA-100001")
    public AssetSummary getAsset(@P("Internal UUID or external asset id") String assetId) {
        return resolve(assetId)
                .map(asset -> new AssetSummary(asset.id().toString(), asset.externalAssetId(), asset.filename(),
                        asset.type().name(), asset.status().name(), Map.of()))
                .orElse(null);
    }

    @Tool("Finds up to 5 assets with an exact metadata value, e.g. field=productType value=SHIRT, optionally ranked "
            + "by a free-text description. Use it to look at how comparable assets are described. Read-only.")
    public List<AssetSummary> searchAssets(@P("Searchable metadata field key, e.g. productType") String field,
                                           @P("Exact value for the field, e.g. SHIRT") String value,
                                           @P(value = "Optional free text for semantic ranking", required = false) String text) {
        AssetSearchCriteria criteria = new AssetSearchCriteria(List.of(new MetadataFilter(field, value)), text, null,
                null, Set.of(), null, null, MAX_RESULTS);
        return searchService.search(criteria).hits().stream().map(AssetTools::summary).toList();
    }

    private Optional<Asset> resolve(String reference) {
        try {
            return Optional.of(assetService.get(UUID.fromString(reference.strip())));
        } catch (IllegalArgumentException notUuid) {
            return assetService.findByExternalAssetId(reference.strip());
        }
    }

    private static AssetSummary summary(AssetSearchHit hit) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        hit.metadata().values().forEach((key, value) -> metadata.put(key, value.toPlainValue()));
        return new AssetSummary(hit.asset().id().toString(), hit.asset().externalAssetId(), hit.asset().filename(),
                hit.asset().type().name(), hit.asset().status().name(), metadata);
    }
}

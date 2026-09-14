package com.example.dam.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Deterministic and hybrid search on PostgreSQL + pgvector. Every test scopes its query with a unique campaign code,
 * which isolates it from data created by other tests in the shared context.
 */
class AssetSearchIT extends AbstractIntegrationTest {

    private String campaign;
    private String blueShirtFront;
    private String blueShirtBack;
    private String redDressFront;

    @BeforeEach
    void createAssets() {
        campaign = "SEARCH_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase().replace('-', 'X') + "_2026";
        blueShirtFront = register("blue-shirt-front.jpg", Map.of("productType", "SHIRT", "color", "BLUE",
                "angleFacing", "FRONT", "copyrightOwner", "Sample Apparel", "usageRightsEnd", LocalDate.now().plusDays(10).toString(),
                "title", "Regular Fit Oxford Shirt", "keywords", List.of("shirt", "oxford", "menswear", "cotton")));
        blueShirtBack = register("blue-shirt-back.jpg", Map.of("productType", "SHIRT", "color", "BLUE",
                "angleFacing", "BACK", "usageRightsEnd", LocalDate.now().plusDays(200).toString(),
                "title", "Regular Fit Oxford Shirt back", "keywords", List.of("shirt", "oxford", "menswear")));
        redDressFront = register("red-dress-front.jpg", Map.of("productType", "DRESS", "color", "RED",
                "angleFacing", "FRONT", "copyrightOwner", "Sample Apparel", "title", "Linen wrap dress for summer",
                "keywords", List.of("dress", "summer", "linen", "womenswear")));
    }

    @Test
    void structuredFiltersAreCaseInsensitiveAndMatchListValues() {
        assertThat(search(Map.of("filters", Map.of("color", "blue", "productType", "shirt"))))
                .containsExactlyInAnyOrder(blueShirtFront, blueShirtBack);
        assertThat(search(Map.of("filters", Map.of("color", "BLUE", "angleFacing", "front"))))
                .containsExactly(blueShirtFront);
        assertThat(search(Map.of("filters", Map.of("keywords", "LINEN")))).containsExactly(redDressFront);
    }

    @Test
    void missingFieldsAndRightsWindowAreDeterministicFilters() {
        assertThat(search(Map.of("missingFields", List.of("copyrightOwner")))).containsExactly(blueShirtBack);
        assertThat(search(Map.of("rightsExpiryAfter", LocalDate.now().toString(),
                "rightsExpiryBefore", LocalDate.now().plusDays(30).toString()))).containsExactly(blueShirtFront);
    }

    @Test
    void hybridSearchRanksFilteredCandidatesBySemanticSimilarity() {
        JsonNode response = searchResponse(Map.of("filters", Map.of("angleFacing", "FRONT"),
                "semanticQuery", "summer dress for women"));

        assertThat(response.get("semanticApplied").asBoolean()).isTrue();
        assertThat(ids(response)).containsExactly(redDressFront, blueShirtFront);
        assertThat(response.get("results").get(0).get("similarity").asDouble())
                .isGreaterThan(response.get("results").get(1).get("similarity").asDouble());
    }

    @Test
    void similarToAssetRanksByEmbeddingAndExcludesTheReference() {
        UUID reference = UUID.fromString(blueShirtFront.split("\\|")[1]);

        JsonNode response = searchResponse(Map.of("similarToAssetId", reference.toString()));

        assertThat(ids(response)).containsExactly(blueShirtBack, redDressFront);
    }

    @Test
    void filteringOnNonSearchableFieldIsRejected() {
        Map<String, Object> body = Map.of("filters", Map.of("altText", "anything"));

        assertThat(post("/api/assets/search", body, USER, 400).get("code").asString()).isEqualTo("UNSEARCHABLE_FIELD");
    }

    private String register(String filename, Map<String, Object> metadata) {
        Map<String, Object> scoped = new HashMap<>(metadata);
        scoped.put("campaign", campaign);
        String externalId = uniqueId("SEARCH");
        UUID id = registerAsset(externalId, AssetType.PRODUCT_IMAGE, filename, scoped);
        return externalId + "|" + id;
    }

    private List<String> search(Map<String, Object> criteria) {
        return ids(searchResponse(criteria));
    }

    private JsonNode searchResponse(Map<String, Object> criteria) {
        Map<String, Object> body = new HashMap<>(criteria);
        @SuppressWarnings("unchecked")
        Map<String, Object> filters = new HashMap<>((Map<String, Object>) criteria.getOrDefault("filters", Map.of()));
        filters.put("campaign", campaign);
        body.put("filters", filters);
        return post("/api/assets/search", body, USER, 200);
    }

    private static List<String> ids(JsonNode response) {
        List<String> ids = new ArrayList<>();
        response.get("results").values()
                .forEach(hit -> ids.add(hit.get("externalAssetId").asString() + "|" + hit.get("id").asString()));
        return ids;
    }
}

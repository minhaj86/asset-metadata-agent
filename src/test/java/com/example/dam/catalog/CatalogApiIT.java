package com.example.dam.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

class CatalogApiIT extends AbstractIntegrationTest {

    @Test
    void catalogFiltersSortsAndPagesWithDerivedSummaries() {
        String prefix = uniqueId("CAT");
        register(prefix + "-A", "complete-shirt.jpg", complete("11111"));
        register(prefix + "-B", "red-dress.jpg", Map.of("productId", "22222", "productType", "DRESS", "color", "RED"));
        Map<String, Object> expired = new LinkedHashMap<>(complete("33333"));
        expired.put("usageRightsEnd", LocalDate.now().minusDays(3).toString());
        register(prefix + "-C", "expired-shirt.jpg", expired);

        JsonNode page = get("/api/catalog/assets?q=" + prefix + "&sort=externalAssetId&direction=asc");
        assertThat(page.get("totalItems").asLong()).isEqualTo(3);
        assertThat(externalIds(page)).containsExactly(prefix + "-A", prefix + "-B", prefix + "-C");
        JsonNode first = page.get("items").get(0);
        assertThat(first.get("summary").get("requiredCompleteness").asDouble()).isEqualTo(100.0);
        assertThat(first.get("summary").get("missingRequiredFields").size()).isZero();
        assertThat(first.get("keyMetadata").get("productId").asString()).isEqualTo("11111");
        assertThat(first.get("rightsStatus").asString()).isEqualTo("VALID");
        assertThat(first.get("aiStatus").asString()).isEqualTo("NOT_ENRICHED");
        assertThat(texts(page.get("items").get(1).get("summary").get("missingRequiredFields")))
                .contains("angleFacing", "copyrightOwner", "usageRightsEnd");

        assertThat(externalIds(get("/api/catalog/assets?q=" + prefix + "&filter=color:blue")))
                .containsExactlyInAnyOrder(prefix + "-A", prefix + "-C");
        assertThat(externalIds(get("/api/catalog/assets?q=" + prefix + "&missingRequired=true")))
                .containsExactly(prefix + "-B");
        assertThat(externalIds(get("/api/catalog/assets?q=" + prefix + "&rightsStatus=EXPIRED")))
                .containsExactly(prefix + "-C");
        assertThat(externalIds(get("/api/catalog/assets?q=" + prefix + "&rightsStatus=MISSING")))
                .containsExactly(prefix + "-B");
        assertThat(externalIds(get("/api/catalog/assets?q=" + prefix + "&missingField=angleFacing")))
                .containsExactly(prefix + "-B");
        assertThat(externalIds(get("/api/catalog/assets?q=" + prefix + "&sort=productId&direction=desc")))
                .containsExactly(prefix + "-C", prefix + "-B", prefix + "-A");

        JsonNode secondPage = get("/api/catalog/assets?q=" + prefix + "&sort=externalAssetId&direction=asc&size=1&page=1");
        assertThat(externalIds(secondPage)).containsExactly(prefix + "-B");
        assertThat(secondPage.get("totalPages").asInt()).isEqualTo(3);

        assertThat(get("/api/catalog/assets?filter=noSuchField:x", 400).get("code").asString())
                .isEqualTo("UNSUPPORTED_FILTER");
        assertThat(get("/api/catalog/assets?sort=altText", 400).get("code").asString()).isEqualTo("UNSUPPORTED_SORT");
    }

    @Test
    void aiStatusFollowsEnrichmentAndReview() {
        UUID assetId = register(uniqueId("CAT"), "blue-shirt-front.jpg", Map.of("productId", "12345"));
        assertThat(get("/api/catalog/assets/" + assetId).get("aiStatus").asString()).isEqualTo("NOT_ENRICHED");

        post("/api/assets/" + assetId + "/enrich", null, USER, 200);

        JsonNode item = get("/api/catalog/assets/" + assetId);
        assertThat(item.get("aiStatus").asString()).isEqualTo("REVIEW_PENDING");
        assertThat(item.get("lastEnrichment").get("status").asString()).isEqualTo("COMPLETED");
        assertThat(item.get("summary").get("aiValueCount").asInt()).isPositive();
        assertThat(item.get("summary").get("pendingReviewCount").asInt()).isPositive();
    }

    @Test
    void taxonomyUsageCountsAssetsPerNodeIncludingDescendants() {
        register(uniqueId("CAT"), "jeans.jpg", Map.of("productType", "JEANS", "taxonomyPath",
                "Fashion > Men > Clothing > Jeans"));

        JsonNode usage = get("/api/catalog/taxonomy-usage");

        JsonNode jeans = node(usage, "Fashion > Men > Clothing > Jeans");
        JsonNode men = node(usage, "Fashion > Men");
        assertThat(jeans.get("assetCount").asLong()).isPositive();
        assertThat(jeans.get("totalAssetCount").asLong()).isEqualTo(jeans.get("assetCount").asLong());
        assertThat(men.get("assetCount").asLong()).isZero();
        assertThat(men.get("totalAssetCount").asLong()).isGreaterThanOrEqualTo(jeans.get("totalAssetCount").asLong());
    }

    @Test
    void bulkValidateEnrichAndExportSelectedAssets() throws Exception {
        UUID shirt = register(uniqueId("BULK"), "blue-shirt-front.jpg", Map.of("productId", "12345"));
        Map<String, Object> risky = new LinkedHashMap<>(complete("44444"));
        risky.put("title", "=HYPERLINK(\"https://example.com\")");
        UUID complete = register(uniqueId("BULK"), "complete-shirt.jpg", risky);
        UUID unknown = UUID.randomUUID();

        JsonNode validation = post("/api/assets/validate", Map.of("assetIds", List.of(shirt, complete, unknown)),
                USER, 200);
        assertThat(validation.size()).isEqualTo(3);
        assertThat(validation.get(0).get("valid").asBoolean()).isFalse();
        assertThat(validation.get(1).get("valid").asBoolean()).isTrue();
        assertThat(validation.get(2).get("error").get("code").asString()).isEqualTo("ASSET_NOT_FOUND");

        JsonNode runs = post("/api/assets/enrich", Map.of("assetIds", List.of(shirt)), USER, 202);
        assertThat(awaitWorkflow(runs.get(0).get("workflowId").asString()).get("status").asString())
                .isEqualTo("COMPLETED");

        String csv = export(List.of(shirt, complete), "csv");
        assertThat(csv).startsWith("externalAssetId,assetType,field,value,source,status,confidence,aiModel,updatedAt,updatedBy");
        assertThat(csv).contains(",productId,12345,MANUAL,APPROVED,");
        assertThat(csv).contains(",title,\"'=HYPERLINK(\"\"https://example.com\"\")\",");
        JsonNode exported = json.readTree(export(List.of(complete), "json"));
        assertThat(exported.get(0).get("metadata").size()).isPositive();
    }

    private UUID register(String externalId, String filename, Map<String, Object> metadata) {
        return registerAsset(externalId, AssetType.PRODUCT_IMAGE, filename, metadata);
    }

    private static Map<String, Object> complete(String productId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("productId", productId);
        metadata.put("productType", "SHIRT");
        metadata.put("color", "BLUE");
        metadata.put("angleFacing", "FRONT");
        metadata.put("shotType", "STUDIO");
        metadata.put("copyrightOwner", "Sample Apparel");
        metadata.put("usageRightsEnd", LocalDate.now().plusYears(1).toString());
        metadata.put("altText", "Front view of a blue shirt on a white background");
        return metadata;
    }

    private String export(List<UUID> assetIds, String format) throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/assets/export")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("assetIds", assetIds, "format", format)))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment");
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static JsonNode node(JsonNode usage, String path) {
        for (JsonNode node : usage.values()) {
            if (node.get("path").asString().equals(path)) {
                return node;
            }
        }
        throw new AssertionError("No taxonomy node " + path);
    }

    private static List<String> externalIds(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").values().forEach(item -> ids.add(item.get("externalAssetId").asString()));
        return ids;
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.values().forEach(value -> values.add(value.asString()));
        return values;
    }
}

package com.example.dam.insights;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

class InsightsApiIT extends AbstractIntegrationTest {

    @Test
    void systemStatusReportsCapabilitiesAndGovernance() {
        JsonNode status = get("/api/system/status");

        assertThat(status.get("ai").get("state").asString()).isEqualTo("AVAILABLE");
        assertThat(status.get("ai").get("provider").asString()).isEqualTo("demo");
        assertThat(status.get("vectorSearch").get("state").asString()).isEqualTo("AVAILABLE");
        assertThat(status.get("dam").get("state").asString()).isEqualTo("AVAILABLE");
        assertThat(status.get("dam").get("provider").asString()).isEqualTo("Mock DAM");
        assertThat(status.get("governance").get("autoApproveThreshold").asDouble()).isEqualTo(0.92);
        assertThat(status.get("governance").get("humanReviewThreshold").asDouble()).isEqualTo(0.70);
        assertThat(status.get("governance").get("schemaVersion").asInt()).isEqualTo(2);
        assertThat(status.get("environment").asString()).isNotBlank();
    }

    @Test
    void dashboardAggregatesMetadataHealthBacklogAndActivity() {
        registerAsset(uniqueId("DASH"), AssetType.PRODUCT_IMAGE, "navy-chinos-back.jpg",
                Map.of("productType", "TROUSERS"));
        UUID enriched = registerAsset(uniqueId("DASH"), AssetType.PRODUCT_IMAGE, "blue-shirt-front.jpg",
                Map.of("productId", "12345"));
        post("/api/assets/" + enriched + "/enrich", null, USER, 200);

        JsonNode dashboard = get("/api/dashboard");

        long total = dashboard.get("assets").get("total").asLong();
        assertThat(total).isGreaterThanOrEqualTo(2);
        assertThat(dashboard.get("assets").get("missingRequired").asLong()).isPositive();
        assertThat(dashboard.get("proposals").get("pendingHumanReview").asLong()).isPositive();
        assertThat(dashboard.get("proposals").get("outcomes").get("autoApproved").asLong()).isPositive();
        assertThat(dashboard.get("enrichment").get("runsToday").asLong()).isPositive();
        assertThat(dashboard.get("enrichment").get("recentRuns").size()).isPositive();
        long bucketed = 0;
        for (JsonNode bucket : dashboard.get("completenessDistribution").values()) {
            bucketed += bucket.get("count").asLong();
        }
        assertThat(dashboard.get("completenessDistribution").size()).isEqualTo(4);
        assertThat(bucketed).isEqualTo(total);
        assertThat(dashboard.get("rights").get("warningWindowDays").asInt()).isEqualTo(30);
    }

    @Test
    void auditLogCanBeSearchedAcrossAssets() {
        String externalId = uniqueId("AUD");
        UUID assetId = registerAsset(externalId, AssetType.PRODUCT_IMAGE, "blue-shirt-front.jpg",
                Map.of("productId", "12345"));
        post("/api/assets/" + assetId + "/enrich", null, USER, 200);

        JsonNode all = get("/api/audit/events?asset=" + externalId.toLowerCase());
        assertThat(all.get("totalItems").asLong()).isGreaterThan(1);
        assertThat(all.get("items").values()).allSatisfy(event ->
                assertThat(event.get("externalAssetId").asString()).isEqualTo(externalId));

        JsonNode aiChanges = get("/api/audit/events?assetId=" + assetId + "&actorType=AI");
        assertThat(aiChanges.get("items").values()).isNotEmpty().allSatisfy(event ->
                assertThat(event.get("actor").asString()).startsWith("ai:"));
        JsonNode keywords = get("/api/audit/events?assetId=" + assetId + "&field=keywords&action=VALUE_AUTO_APPLIED");
        assertThat(keywords.get("totalItems").asLong()).isEqualTo(1);
        JsonNode paged = get("/api/audit/events?assetId=" + assetId + "&size=1&page=0");
        assertThat(paged.get("items").size()).isEqualTo(1);
        assertThat(paged.get("totalPages").asLong()).isEqualTo(all.get("totalItems").asLong());
    }

    @Test
    void mockDamServesGeneratedRenditionsForItsAssets() throws Exception {
        MvcResult rendition = mvc.perform(MockMvcRequestBuilders
                .get("/mock-dam/assets/DA-200001/blue-shirt-front.jpg")).andReturn();

        assertThat(rendition.getResponse().getStatus()).isEqualTo(200);
        assertThat(rendition.getResponse().getContentType()).startsWith("image/svg+xml");
        assertThat(rendition.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("MOCK DAM PREVIEW", "DA-200001");
        get("/mock-dam/assets/DA-200001/other.jpg", 404);
    }

    @Test
    void clientRoutesFallBackToTheWebAppWhileUnknownApiPathsStay404() throws Exception {
        MvcResult page = mvc.perform(MockMvcRequestBuilders.get("/assets/" + UUID.randomUUID())).andReturn();

        assertThat(page.getResponse().getStatus()).isEqualTo(200);
        assertThat(page.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("spa-test-shell");
        get("/api/does-not-exist", 404);
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/missing-file.js")).andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }
}

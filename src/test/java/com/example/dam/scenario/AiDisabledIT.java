package com.example.dam.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/** With the AI layer switched off the DAM keeps working: CRUD, validation and structured search stay available. */
@TestPropertySource(properties = "ai.enabled=false")
class AiDisabledIT extends AbstractIntegrationTest {

    @Test
    void coreMetadataManagementWorksWithoutAi() {
        UUID asset = registerAsset(uniqueId("NOAI"), AssetType.PRODUCT_IMAGE, "blue-shirt-front.jpg",
                Map.of("productId", "12345", "color", "BLUE"));
        put("/api/assets/" + asset + "/metadata/angleFacing", Map.of("value", "FRONT"), USER, 200);

        assertThat(post("/api/assets/" + asset + "/validate", null, USER, 200).get("valid").asBoolean()).isFalse();
        assertThat(get("/api/assets/" + asset + "/metadata/history").size()).isEqualTo(3);
        JsonNode search = post("/api/assets/search", Map.of("filters", Map.of("color", "BLUE", "angleFacing", "FRONT")),
                USER, 200);
        assertThat(search.get("count").asInt()).isPositive();
    }

    @Test
    void aiFeaturesDegradeInsteadOfFailing() {
        UUID asset = registerAsset(uniqueId("NOAI"), AssetType.PRODUCT_IMAGE, "shirt.jpg", Map.of("productId", "12345"));

        assertThat(post("/api/assets/" + asset + "/enrich", null, USER, 503).get("code").asString()).isEqualTo("AI_DISABLED");
        JsonNode quality = get("/api/assets/" + asset + "/quality");
        assertThat(quality.get("aiStatus").asString()).isEqualTo("UNAVAILABLE");
        assertThat(quality.get("deterministicErrors").size()).isPositive();
        JsonNode search = post("/api/assets/semantic-search", Map.of("query", "blue shirts"), USER, 200);
        assertThat(search.get("interpretedByAi").asBoolean()).isFalse();
        assertThat(search.get("search").get("degraded").asBoolean()).isTrue();
    }
}

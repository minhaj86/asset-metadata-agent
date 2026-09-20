package com.example.dam.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The reference scenario: a PRODUCT_IMAGE "blue-shirt-front.jpg" arrives with only productId=12345; the agentic
 * workflow enriches it, safe high-confidence fields are applied, sensitive ones wait for approval, and after
 * approval the asset is found by "front-facing blue men's shirts".
 */
class EndToEndScenarioIT extends AbstractIntegrationTest {

    @Test
    void blueShirtArrivesIsEnrichedApprovedAndFoundByNaturalLanguage() {
        JsonNode asset = post("/api/assets",
                Map.of("externalAssetId", "DA-200001", "type", "PRODUCT_IMAGE", "metadata", Map.of("productId", "12345")),
                USER, 201);
        String id = asset.get("id").asString();
        assertThat(asset.get("filename").asString()).isEqualTo("blue-shirt-front.jpg");

        JsonNode enrichment = post("/api/assets/" + id + "/enrich", null, USER, 200);

        assertThat(route(enrichment, "color")).isEqualTo("AUTO_APPROVE");
        assertThat(route(enrichment, "backgroundType")).isEqualTo("AUTO_APPROVE");
        assertThat(route(enrichment, "keywords")).isEqualTo("AUTO_APPROVE");
        assertThat(value(enrichment, "keywords").toString()).isEqualTo("[\"shirt\",\"oxford\",\"blue\",\"menswear\",\"cotton\"]");
        assertThat(route(enrichment, "angleFacing")).isEqualTo("HUMAN_REVIEW");
        assertThat(route(enrichment, "copyrightOwner")).isEqualTo("HUMAN_REVIEW");
        assertThat(route(enrichment, "taxonomyPath")).isEqualTo("HUMAN_REVIEW");
        assertThat(value(enrichment, "taxonomyPath").asString()).isEqualTo("Fashion > Men > Clothing > Shirts");
        assertThat(value(enrichment, "title").asString()).isEqualTo("Regular Fit Oxford Shirt - Front View");
        assertThat(value(enrichment, "altText").asString())
                .isEqualTo("Front view of a light blue men's Oxford shirt on a white background");
        assertThat(enrichment.get("assetStatus").asString()).isEqualTo("IN_REVIEW");

        String angleProposal = proposalId(enrichment, "angleFacing");
        post("/api/metadata/proposals/" + angleProposal + "/approve", Map.of("comment", "Front view confirmed"),
                REVIEWER, 200);

        JsonNode search = post("/api/assets/semantic-search", Map.of("query", "front-facing blue men's shirts"), USER, 200);
        assertThat(search.get("search").get("semanticApplied").asBoolean()).isTrue();
        List<String> found = new ArrayList<>();
        search.get("search").get("results").values().forEach(hit -> found.add(hit.get("externalAssetId").asString()));
        assertThat(found).contains("DA-200001");

        JsonNode history = get("/api/assets/" + id + "/metadata/history");
        List<String> actions = new ArrayList<>();
        history.values().forEach(event -> actions.add(event.get("action").asString()));
        assertThat(actions).contains("VALUE_IMPORTED", "VALUE_AUTO_APPLIED", "PROPOSAL_APPROVED");
    }

    private static JsonNode proposal(JsonNode enrichment, String field) {
        for (JsonNode proposal : enrichment.get("proposals").values()) {
            if (proposal.get("field").asString().equals(field)) {
                return proposal;
            }
        }
        throw new AssertionError("No proposal for " + field);
    }

    private static String route(JsonNode enrichment, String field) {
        return proposal(enrichment, field).get("route").asString();
    }

    private static JsonNode value(JsonNode enrichment, String field) {
        return proposal(enrichment, field).get("value");
    }

    private static String proposalId(JsonNode enrichment, String field) {
        return proposal(enrichment, field).get("proposalId").asString();
    }
}

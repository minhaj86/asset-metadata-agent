package com.example.dam.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.ai.agent.EnrichmentOutput;
import com.example.dam.ai.agent.ExtractionOutput;
import com.example.dam.ai.agent.FieldSuggestion;
import com.example.dam.ai.agent.QualityReviewOutput;
import com.example.dam.ai.agent.TaxonomyClassification;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The LangChain4j Agentic enrichment workflow end to end (offline demo model behind a scriptable test double):
 * AI proposes, deterministic policy routes, humans approve sensitive changes, everything is audited.
 */
class MetadataEnrichmentWorkflowIT extends AbstractIntegrationTest {

    @Test
    void aiProposesMetadataWithConfidenceExplanationAndEvidence() {
        JsonNode result = enrich(shirtAsset());

        assertThat(result.get("proposals").size()).isGreaterThan(5);
        result.get("proposals").values().forEach(proposal -> {
            assertThat(proposal.get("confidence").asDouble()).isBetween(0.0, 1.0);
            assertThat(proposal.get("explanation").asString()).isNotBlank();
            assertThat(proposal.get("evidence").size()).isPositive();
        });
        assertThat(stepOutcomes(result)).contains(
                "loadAssetContext=SUCCEEDED", "metadataExtractionAgent=SUCCEEDED", "taxonomyAgent=SUCCEEDED",
                "metadataEnrichmentAgent=SUCCEEDED", "consolidateProposals=SUCCEEDED", "metadataQualityAgent=SUCCEEDED",
                "approvalDecision=SUCCEEDED");
        assertThat(toolCalls(result, "taxonomyAgent")).contains("searchTaxonomy");
        assertThat(toolCalls(result, "metadataEnrichmentAgent")).contains("searchMetadataDocumentation");
    }

    @Test
    void highConfidenceSafeFieldIsAutoApproved() {
        UUID asset = shirtAsset();

        JsonNode color = proposal(enrich(asset), "color");

        assertThat(color.get("route").asString()).isEqualTo("AUTO_APPROVE");
        assertThat(color.get("status").asString()).isEqualTo("APPROVED");
        JsonNode stored = metadataValue(asset, "color");
        assertThat(stored.get("value").asString()).isEqualTo("BLUE");
        assertThat(stored.get("source").asString()).isEqualTo("AI_GENERATED");
        assertThat(stored.get("status").asString()).isEqualTo("VALIDATED");
        assertThat(stored.get("confidence").asDouble()).isEqualTo(0.95);
        JsonNode audit = historyEntry(asset, "color");
        assertThat(audit.get("action").asString()).isEqualTo("VALUE_AUTO_APPLIED");
        assertThat(audit.get("approvedBy").asString()).isEqualTo("system:approval-policy");
        assertThat(audit.get("aiModel").asString()).isNotBlank();
    }

    @Test
    void sensitiveFieldRequiresHumanApproval() {
        UUID asset = shirtAsset();

        JsonNode angle = proposal(enrich(asset), "angleFacing");

        assertThat(angle.get("confidence").asDouble()).isGreaterThanOrEqualTo(0.92);
        assertThat(angle.get("route").asString()).isEqualTo("HUMAN_REVIEW");
        assertThat(angle.get("status").asString()).isEqualTo("PROPOSED");
        assertThat(metadataValue(asset, "angleFacing")).isNull();
        assertThat(get("/api/assets/" + asset).get("status").asString()).isEqualTo("IN_REVIEW");

        String proposalId = angle.get("proposalId").asString();
        assertThat(post("/api/metadata/proposals/" + proposalId + "/approve", Map.of(), null, 403).get("code").asString())
                .isEqualTo("REVIEWER_REQUIRED");
        post("/api/metadata/proposals/" + proposalId + "/approve", Map.of("comment", "Matches the image"), REVIEWER, 200);

        JsonNode stored = metadataValue(asset, "angleFacing");
        assertThat(stored.get("status").asString()).isEqualTo("APPROVED");
        assertThat(stored.get("updatedBy").asString()).isEqualTo(REVIEWER);
        JsonNode audit = historyEntry(asset, "angleFacing");
        assertThat(audit.get("action").asString()).isEqualTo("PROPOSAL_APPROVED");
        assertThat(audit.get("approvedBy").asString()).isEqualTo(REVIEWER);
        assertThat(audit.get("reason").asString()).isEqualTo("Matches the image");
        post("/api/metadata/proposals/" + proposalId + "/approve", Map.of(), REVIEWER, 409);
    }

    @Test
    void inventedTaxonomyNodeIsRejected() {
        chatModel.respond(TaxonomyClassification.class, new TaxonomyClassification("fashion.men.clothing.capes",
                "Fashion > Men > Clothing > Capes", 0.97, "Looks like a cape", List.of("vibes")));

        JsonNode result = enrich(shirtAsset());

        assertThat(proposalOrNull(result, "taxonomyPath")).isNull();
        assertThat(result.get("taxonomy").get("accepted").asBoolean()).isFalse();
        assertThat(result.get("taxonomy").get("rejectionReason").asString()).contains("does not exist");
        assertThat(texts(result.get("warnings"))).anyMatch(warning -> warning.contains("fashion.men.clothing.capes"));
    }

    @Test
    void lowConfidenceSuggestionIsNotPersistedAsMetadata() {
        chatModel.respond(EnrichmentOutput.class, new EnrichmentOutput(List.of(new FieldSuggestion("keywords",
                List.of("shirt", "casual"), 0.55, "Weak guess from the filename", List.of("filename"))), "weak"));
        UUID asset = shirtAsset();

        JsonNode keywords = proposal(enrich(asset), "keywords");

        assertThat(keywords.get("route").asString()).isEqualTo("LOW_CONFIDENCE");
        assertThat(keywords.get("status").asString()).isEqualTo("PROPOSED");
        assertThat(metadataValue(asset, "keywords")).isNull();
        JsonNode suggestions = get("/api/metadata/proposals?assetId=" + asset + "&route=LOW_CONFIDENCE");
        assertThat(suggestions.size()).isEqualTo(1);
    }

    @Test
    void aiCanNeitherChangeRightsNorInventProductAssociationsNorOverwriteAuthoritativeValues() {
        chatModel.respond(ExtractionOutput.class, new ExtractionOutput(List.of(
                new FieldSuggestion("usageRightsEnd", List.of("2099-12-31"), 0.99, "Longer is better", List.of("none")),
                new FieldSuggestion("productId", List.of("99999"), 0.99, "Similar product", List.of("none")),
                new FieldSuggestion("color", List.of("BLUE"), 0.99, "Image analysis", List.of("imageAnalysis"))), "x"));
        UUID asset = registerAsset(uniqueId("WF"), AssetType.PRODUCT_IMAGE, "red-shirt-front.jpg",
                Map.of("productId", "12345", "color", "RED"));

        JsonNode result = enrich(asset);

        assertThat(proposal(result, "usageRightsEnd").get("route").asString()).isEqualTo("REJECTED_BY_POLICY");
        assertThat(proposal(result, "productId").get("route").asString()).isEqualTo("REJECTED_BY_POLICY");
        JsonNode color = proposal(result, "color");
        assertThat(color.get("route").asString()).isEqualTo("REJECTED_BY_POLICY");
        assertThat(color.get("reason").asString()).contains("authoritative");
        assertThat(metadataValue(asset, "color").get("value").asString()).isEqualTo("RED");
        assertThat(metadataValue(asset, "productId").get("value").asString()).isEqualTo("12345");
        assertThat(metadataValue(asset, "usageRightsEnd")).isNull();
    }

    @Test
    void workflowDegradesGracefullyWhenOneAgentIsUnavailable() {
        chatModel.fail(TaxonomyClassification.class);

        JsonNode result = enrich(shirtAsset());

        assertThat(stepOutcomes(result)).contains("taxonomyAgent=FAILED", "metadataEnrichmentAgent=SUCCEEDED");
        assertThat(texts(result.get("warnings"))).anyMatch(warning -> warning.startsWith("taxonomyAgent unavailable"));
        assertThat(proposal(result, "color").get("route").asString()).isEqualTo("AUTO_APPROVE");
    }

    @Test
    void malformedModelOutputIsRetriedOnceThenSkipped() {
        chatModel.respondRaw(QualityReviewOutput.class, "Sure! Here is my review: looks great.");

        JsonNode result = enrich(shirtAsset());

        assertThat(chatModel.requestsFor(QualityReviewOutput.class)).isEqualTo(2);
        assertThat(result.get("qualityReview").isNull()).isTrue();
        assertThat(texts(result.get("warnings"))).anyMatch(warning -> warning.startsWith("metadataQualityAgent unavailable"));
    }

    @Test
    void enrichmentAnswers503AndChangesNothingWhenAllAiAgentsFail() {
        chatModel.fail(ExtractionOutput.class);
        chatModel.fail(TaxonomyClassification.class);
        chatModel.fail(EnrichmentOutput.class);
        chatModel.fail(QualityReviewOutput.class);
        UUID asset = shirtAsset();

        JsonNode problem = post("/api/assets/" + asset + "/enrich", null, USER, 503);

        assertThat(problem.get("code").asString()).isEqualTo("AI_UNAVAILABLE");
        assertThat(get("/api/metadata/proposals?assetId=" + asset).size()).isZero();
        assertThat(get("/api/assets/" + asset + "/metadata").get("metadata").size()).isEqualTo(1);
    }

    private UUID shirtAsset() {
        return registerAsset(uniqueId("WF"), AssetType.PRODUCT_IMAGE, "blue-shirt-front.jpg", Map.of("productId", "12345"));
    }

    private JsonNode enrich(UUID asset) {
        return post("/api/assets/" + asset + "/enrich", null, USER, 200);
    }

    private static JsonNode proposal(JsonNode result, String field) {
        JsonNode proposal = proposalOrNull(result, field);
        assertThat(proposal).as("proposal for " + field).isNotNull();
        return proposal;
    }

    private static JsonNode proposalOrNull(JsonNode result, String field) {
        for (JsonNode proposal : result.get("proposals").values()) {
            if (proposal.get("field").asString().equals(field)) {
                return proposal;
            }
        }
        return null;
    }

    private JsonNode historyEntry(UUID asset, String field) {
        for (JsonNode event : get("/api/assets/" + asset + "/metadata/history").values()) {
            if (event.get("field").asString().equals(field)) {
                return event;
            }
        }
        throw new AssertionError("No audit event for " + field);
    }

    private static List<String> stepOutcomes(JsonNode result) {
        List<String> outcomes = new ArrayList<>();
        result.get("agentSteps").values()
                .forEach(step -> outcomes.add(step.get("name").asString() + "=" + step.get("outcome").asString()));
        return outcomes;
    }

    private static List<String> toolCalls(JsonNode result, String agent) {
        for (JsonNode step : result.get("agentSteps").values()) {
            if (step.get("name").asString().equals(agent)) {
                return texts(step.get("toolCalls"));
            }
        }
        return List.of();
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.values().forEach(value -> values.add(value.asString()));
        return values;
    }
}

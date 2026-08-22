package com.example.dam.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ProposalReviewApiIT extends AbstractIntegrationTest {

    @Test
    void proposalsCarryBackendReviewPolicyAndFieldContext() {
        UUID assetId = enrichedShirt();

        JsonNode taxonomy = proposal(assetId, "taxonomyPath");
        assertThat(taxonomy.get("policy").get("requiresHumanApproval").asBoolean()).isTrue();
        assertThat(taxonomy.get("policy").get("canAutoApprove").asBoolean()).isFalse();
        assertThat(taxonomy.get("policy").get("bulkApprovable").asBoolean()).isFalse();
        assertThat(taxonomy.get("policy").get("riskLevel").asString()).isEqualTo("HIGH");
        assertThat(taxonomy.get("fieldInfo").get("displayName").asString()).isEqualTo("Taxonomy path");
        assertThat(taxonomy.get("fieldInfo").get("group").asString()).isEqualTo("PRODUCT");
        assertThat(taxonomy.get("externalAssetId").asString()).startsWith("REV-");
        assertThat(taxonomy.get("proposedBy").asString()).isEqualTo("taxonomyAgent");

        JsonNode category = proposal(assetId, "category");
        assertThat(category.get("policy").get("canAutoApprove").asBoolean()).isTrue();
        assertThat(category.get("policy").get("bulkApprovable").asBoolean()).isTrue();
        assertThat(category.get("policy").get("riskLevel").asString()).isEqualTo("LOW");
        assertThat(category.get("fieldInfo").get("allowedValues").size()).isPositive();
    }

    @Test
    void bulkApprovalOnlyAppliesPolicyEligibleProposals() {
        UUID assetId = enrichedShirt();
        JsonNode open = get("/api/metadata/proposals?assetId=" + assetId);
        List<String> ids = new ArrayList<>();
        long eligible = 0;
        for (JsonNode proposal : open.values()) {
            ids.add(proposal.get("id").asString());
            if (proposal.get("policy").get("bulkApprovable").asBoolean()) {
                eligible++;
            }
        }
        assertThat(eligible).isPositive().isLessThan(ids.size());

        post("/api/metadata/proposals/bulk-approve", Map.of("proposalIds", ids), null, 403);
        JsonNode result = post("/api/metadata/proposals/bulk-approve", Map.of("proposalIds", ids,
                "comment", "Batch review"), REVIEWER, 200);

        assertThat(result.get("approved").asLong()).isEqualTo(eligible);
        assertThat(result.get("skipped").asLong()).isEqualTo(ids.size() - eligible);
        assertThat(result.get("items").values()).filteredOn(item -> item.get("outcome").asString().equals("SKIPPED"))
                .allSatisfy(item -> assertThat(item.get("code").asString()).isEqualTo("NOT_BULK_APPROVABLE"));
        assertThat(proposal(assetId, "taxonomyPath").get("status").asString()).isEqualTo("PROPOSED");
    }

    @Test
    void reviewerCanEditAProposalBeforeApproving() {
        UUID assetId = enrichedShirt();
        JsonNode altText = proposal(assetId, "altText");
        String edited = "Front view of a light blue Oxford shirt with button-down collar on white";

        JsonNode approved = post("/api/metadata/proposals/" + altText.get("id").asString() + "/approve",
                Map.of("comment", "Mention the collar", "value", edited), REVIEWER, 200);

        assertThat(approved.get("status").asString()).isEqualTo("APPROVED");
        assertThat(approved.get("appliedValue").asString()).isEqualTo(edited);
        assertThat(approved.get("proposedValue").asString()).isEqualTo(altText.get("proposedValue").asString());
        JsonNode stored = metadataValue(assetId, "altText");
        assertThat(stored.get("value").asString()).isEqualTo(edited);
        assertThat(stored.get("source").asString()).isEqualTo("MANUAL");
        assertThat(stored.get("authoritative").asBoolean()).isTrue();
        assertThat(stored.get("origin").get("editedByReviewer").asBoolean()).isTrue();
        JsonNode history = get("/api/assets/" + assetId + "/metadata/history?field=altText");
        assertThat(history.values()).allSatisfy(event -> assertThat(event.get("field").asString()).isEqualTo("altText"));
        assertThat(history.get(0).get("reason").asString()).isEqualTo("Approved with reviewer edits: Mention the collar");
    }

    @Test
    void editedValuesAreValidatedAgainstTheSchema() {
        UUID assetId = enrichedShirt();
        String angleId = proposal(assetId, "angleFacing").get("id").asString();

        JsonNode problem = post("/api/metadata/proposals/" + angleId + "/approve", Map.of("value", "DIAGONAL"),
                REVIEWER, 422);

        assertThat(problem.get("code").asString()).isEqualTo("METADATA_VALIDATION_FAILED");
        assertThat(proposal(assetId, "angleFacing").get("status").asString()).isEqualTo("PROPOSED");
    }

    @Test
    void aiAppliedValuesExposeTheirOrigin() {
        UUID assetId = enrichedShirt();

        JsonNode background = metadataValue(assetId, "backgroundType");

        assertThat(background.get("source").asString()).isEqualTo("AI_GENERATED");
        assertThat(background.get("origin").get("agent").asString()).isEqualTo("metadataExtractionAgent");
        assertThat(background.get("origin").get("route").asString()).isEqualTo("AUTO_APPROVE");
        assertThat(background.get("origin").get("explanation").asString()).isNotBlank();
        assertThat(background.get("origin").get("evidence").size()).isPositive();
        assertThat(metadataValue(assetId, "productId").get("origin").isNull()).isTrue();
    }

    @Test
    void proposalsCanBeFilteredByStatusRiskAndAgent() {
        UUID assetId = enrichedShirt();

        assertThat(fields(get("/api/metadata/proposals?status=ALL&assetId=" + assetId))).contains("backgroundType");
        JsonNode highRisk = get("/api/metadata/proposals?riskLevel=HIGH&assetId=" + assetId);
        assertThat(fields(highRisk)).contains("angleFacing", "taxonomyPath").doesNotContain("category", "altText");
        assertThat(highRisk.values()).allSatisfy(proposal ->
                assertThat(proposal.get("policy").get("requiresHumanApproval").asBoolean()).isTrue());
        assertThat(fields(get("/api/metadata/proposals?agent=taxonomyAgent&assetId=" + assetId)))
                .containsExactly("taxonomyPath");
        assertThat(fields(get("/api/metadata/proposals?minConfidence=0.95&status=ALL&assetId=" + assetId)))
                .isNotEmpty().doesNotContain("altText");
        assertThat(get("/api/metadata/proposals?status=MAYBE", 400).get("code").asString()).isEqualTo("INVALID_STATUS");
    }

    private UUID enrichedShirt() {
        UUID assetId = registerAsset(uniqueId("REV"), AssetType.PRODUCT_IMAGE, "blue-shirt-front.jpg",
                Map.of("productId", "12345"));
        post("/api/assets/" + assetId + "/enrich", null, USER, 200);
        return assetId;
    }

    private JsonNode proposal(UUID assetId, String field) {
        for (JsonNode proposal : get("/api/metadata/proposals?status=ALL&assetId=" + assetId).values()) {
            if (proposal.get("field").asString().equals(field)) {
                return proposal;
            }
        }
        throw new AssertionError("No proposal for " + field);
    }

    private static List<String> fields(JsonNode proposals) {
        List<String> fields = new ArrayList<>();
        proposals.values().forEach(proposal -> fields.add(proposal.get("field").asString()));
        return fields;
    }
}

package com.example.dam.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.ai.agent.EnrichmentOutput;
import com.example.dam.ai.agent.ExtractionOutput;
import com.example.dam.ai.agent.QualityReviewOutput;
import com.example.dam.ai.agent.TaxonomyClassification;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;

class WorkflowRunApiIT extends AbstractIntegrationTest {

    private static final List<String> PLANNED_STAGES = List.of("loadAssetContext", "metadataExtractionAgent",
            "mergeWithExistingMetadata", "taxonomyAgent", "metadataEnrichmentAgent", "consolidateProposals",
            "metadataQualityAgent", "approvalDecision", "autoApprovalRouting", "humanReviewRouting", "recordResults");

    @Test
    void asyncEnrichmentIsTrackedStageByStageUntilCompletion() throws Exception {
        UUID assetId = shirtAsset();

        MvcResult started = mvc.perform(MockMvcRequestBuilders.post("/api/assets/" + assetId + "/enrich")
                .header("Prefer", "respond-async").header("X-User-Id", USER)).andReturn();

        assertThat(started.getResponse().getStatus()).isEqualTo(202);
        JsonNode accepted = json.readTree(started.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String workflowId = accepted.get("workflowId").asString();
        assertThat(started.getResponse().getHeader("Location")).isEqualTo("/api/workflows/" + workflowId);
        assertThat(accepted.get("workflowType").asString()).isEqualTo("METADATA_ENRICHMENT");
        assertThat(stageIds(accepted)).containsExactlyElementsOf(PLANNED_STAGES);

        JsonNode finished = awaitWorkflow(workflowId);
        assertThat(finished.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(finished.get("requestedBy").asString()).isEqualTo(USER);
        assertThat(stage(finished, "metadataExtractionAgent").get("status").asString()).isEqualTo("COMPLETED");
        assertThat(stage(finished, "metadataExtractionAgent").get("kind").asString()).isEqualTo("AI_AGENT");
        assertThat(texts(stage(finished, "taxonomyAgent").get("toolCalls"))).contains("searchTaxonomy");
        assertThat(stage(finished, "taxonomyAgent").get("parallelGroup").asString()).isEqualTo("parallelReasoning");
        assertThat(stage(finished, "recordResults").get("status").asString()).isEqualTo("COMPLETED");
        assertThat(finished.get("summary").get("autoApplied").asInt()).isPositive();
        assertThat(finished.get("summary").get("pendingReview").asInt()).isPositive();
        assertThat(finished.get("durationMillis").asLong()).isNotNegative();

        JsonNode runs = get("/api/workflows?assetId=" + assetId);
        assertThat(runs.size()).isEqualTo(1);
        assertThat(runs.get(0).get("workflowId").asString()).isEqualTo(workflowId);
        JsonNode proposals = get("/api/metadata/proposals?workflowRunId=" + workflowId + "&status=ALL");
        JsonNode summary = finished.get("summary");
        assertThat(proposals.size()).isEqualTo(summary.get("autoApplied").asInt() + summary.get("pendingReview").asInt()
                + summary.get("lowConfidenceSuggestions").asInt() + summary.get("rejectedByPolicy").asInt());
    }

    @Test
    void synchronousEnrichmentIsTrackedAsWell() {
        UUID assetId = shirtAsset();

        String workflowId = post("/api/assets/" + assetId + "/enrich", null, USER, 200).get("workflowRunId").asString();

        JsonNode run = get("/api/workflows/" + workflowId);
        assertThat(run.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(run.get("externalAssetId").asString()).startsWith("WF-");
    }

    @Test
    void serverSentEventsDeliverRunSnapshotsUntilTheRunIsFinished() throws Exception {
        UUID assetId = shirtAsset();
        String workflowId = post("/api/assets/" + assetId + "/enrich", null, USER, 200).get("workflowRunId").asString();

        MvcResult events = mvc.perform(MockMvcRequestBuilders.get("/api/workflows/" + workflowId + "/events"))
                .andReturn();

        String stream = events.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(events.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(stream).contains("event:workflow");
        String data = stream.lines().filter(line -> line.startsWith("data:")).reduce((first, last) -> last)
                .orElseThrow().substring("data:".length());
        assertThat(json.readTree(data).get("status").asString()).isEqualTo("COMPLETED");
    }

    @Test
    void failedRunsReportTheReasonAndCloseOpenStages() {
        chatModel.fail(ExtractionOutput.class);
        chatModel.fail(EnrichmentOutput.class);
        chatModel.fail(TaxonomyClassification.class);
        chatModel.fail(QualityReviewOutput.class);
        UUID assetId = shirtAsset();

        JsonNode failed = awaitWorkflow(startEnrichment(assetId).get("workflowId").asString());

        assertThat(failed.get("status").asString()).isEqualTo("FAILED");
        assertThat(failed.get("error").get("code").asString()).isEqualTo("AI_UNAVAILABLE");
        assertThat(stage(failed, "metadataExtractionAgent").get("status").asString()).isEqualTo("FAILED");
        assertThat(stage(failed, "recordResults").get("status").asString()).isEqualTo("SKIPPED");
        assertThat(get("/api/metadata/proposals?assetId=" + assetId + "&status=ALL").size()).isZero();
    }

    @Test
    void workflowPlanPublishesTheAgenticTopology() {
        JsonNode plan = get("/api/workflows/plan");

        List<String> ids = new ArrayList<>();
        plan.values().forEach(stage -> ids.add(stage.get("id").asString()));
        assertThat(ids).containsExactlyElementsOf(PLANNED_STAGES);
        assertThat(plan.get(3).get("conditional").asBoolean()).isTrue();
        assertThat(plan.get(4).get("parallelGroup").asString()).isEqualTo("parallelReasoning");
    }

    @Test
    void unknownWorkflowIsNotFound() {
        JsonNode problem = get("/api/workflows/" + UUID.randomUUID(), 404);

        assertThat(problem.get("code").asString()).isEqualTo("WORKFLOW_NOT_FOUND");
    }

    private UUID shirtAsset() {
        return registerAsset(uniqueId("WF"), AssetType.PRODUCT_IMAGE, "blue-shirt-front.jpg",
                Map.of("productId", "12345"));
    }

    private static List<String> stageIds(JsonNode run) {
        List<String> ids = new ArrayList<>();
        run.get("stages").values().forEach(stage -> ids.add(stage.get("id").asString()));
        return ids;
    }

    private static JsonNode stage(JsonNode run, String id) {
        for (JsonNode stage : run.get("stages").values()) {
            if (stage.get("id").asString().equals(id)) {
                return stage;
            }
        }
        throw new AssertionError("No stage " + id);
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.values().forEach(value -> values.add(value.asString()));
        return values;
    }
}

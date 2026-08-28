package com.example.dam.metadata.application.enrichment;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetNotFoundException;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.common.domain.Actor;
import com.example.dam.common.domain.CapabilityUnavailableException;
import com.example.dam.common.domain.DamException;
import com.example.dam.common.domain.InvalidRequestException;
import com.example.dam.metadata.application.enrichment.AgentStepTrace.Outcome;
import com.example.dam.metadata.application.enrichment.AgentStepTrace.StepKind;
import com.example.dam.metadata.application.enrichment.EnrichmentOutcome.ProposalOutcome;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunSummary;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Application service for AI-assisted enrichment. Every execution is a tracked {@link EnrichmentRun}. The
 * (long-running) AI workflow runs outside any database transaction; its proposals are then evaluated and persisted
 * atomically by {@link EnrichmentResultRecorder}, which is the final, deterministic stage of every run.
 */
@Service
public class MetadataEnrichmentService {

    /** Application-owned final stage: deterministic policy evaluation and persistence. */
    public static final WorkflowStageDefinition RECORD_STAGE = new WorkflowStageDefinition("recordResults",
            "Apply policy & record proposals", StepKind.DETERMINISTIC_STEP,
            "Re-evaluates every proposal against the latest metadata, applies permitted values and audits", null,
            false);

    private static final Logger log = LoggerFactory.getLogger(MetadataEnrichmentService.class);

    private final AssetRepository assetRepository;
    private final ObjectProvider<MetadataEnrichmentWorkflow> workflow;
    private final EnrichmentResultRecorder recorder;
    private final EnrichmentRunTracker tracker;
    private final EnrichmentRunExecutor executor;
    private final EnrichmentProperties properties;

    public MetadataEnrichmentService(AssetRepository assetRepository,
                                     ObjectProvider<MetadataEnrichmentWorkflow> workflow,
                                     EnrichmentResultRecorder recorder, EnrichmentRunTracker tracker,
                                     EnrichmentRunExecutor executor, EnrichmentProperties properties) {
        this.assetRepository = assetRepository;
        this.workflow = workflow;
        this.recorder = recorder;
        this.tracker = tracker;
        this.executor = executor;
        this.properties = properties;
    }

    /** Runs enrichment synchronously and returns the recorded outcome. */
    public EnrichmentOutcome enrich(UUID assetId, Actor requestedBy) {
        Asset asset = requireAsset(assetId);
        MetadataEnrichmentWorkflow enrichmentWorkflow = requireWorkflow();
        EnrichmentRun run = tracker.create(asset, requestedBy, plan(enrichmentWorkflow), enrichmentWorkflow.aiModel());
        return execute(run.id(), asset, enrichmentWorkflow, requestedBy);
    }

    /** Starts enrichment in the background; progress is observable through {@link EnrichmentRunTracker}. */
    public EnrichmentRun start(UUID assetId, Actor requestedBy) {
        Asset asset = requireAsset(assetId);
        MetadataEnrichmentWorkflow enrichmentWorkflow = requireWorkflow();
        EnrichmentRun run = tracker.create(asset, requestedBy, plan(enrichmentWorkflow), enrichmentWorkflow.aiModel());
        executor.submit(() -> execute(run.id(), asset, enrichmentWorkflow, requestedBy));
        return run;
    }

    /**
     * Starts one background run per asset. Bulk enrichment only creates proposals: the same deterministic policy
     * applies, so sensitive fields always wait for a human decision.
     */
    public List<EnrichmentRun> startAll(List<UUID> assetIds, Actor requestedBy) {
        List<UUID> distinct = List.copyOf(new LinkedHashSet<>(assetIds));
        if (distinct.size() > properties.maxBulkAssets()) {
            throw new InvalidRequestException("BULK_LIMIT_EXCEEDED",
                    "At most %d assets per bulk enrichment".formatted(properties.maxBulkAssets()));
        }
        distinct.forEach(this::requireAsset);
        List<EnrichmentRun> runs = new ArrayList<>();
        for (UUID assetId : distinct) {
            runs.add(start(assetId, requestedBy));
        }
        return runs;
    }

    /** Planned stages of the workflow including the application-owned recording stage. */
    public List<WorkflowStageDefinition> plan() {
        return plan(requireWorkflow());
    }

    private EnrichmentOutcome execute(UUID runId, Asset asset, MetadataEnrichmentWorkflow enrichmentWorkflow,
                                      Actor requestedBy) {
        try {
            tracker.markRunning(runId);
            MetadataEnrichmentResult result = enrichmentWorkflow.enrich(asset.id(), runId);
            if (result.allAiAgentsFailed()) {
                throw new CapabilityUnavailableException("AI_UNAVAILABLE",
                        "All AI agents failed, no metadata was changed: " + String.join("; ", result.warnings()));
            }
            tracker.stageStarted(runId, RECORD_STAGE.id());
            long start = System.nanoTime();
            EnrichmentOutcome outcome = recorder.record(result, requestedBy);
            RunSummary summary = summary(outcome.proposals());
            tracker.stageFinished(runId, new AgentStepTrace(RECORD_STAGE.id(), StepKind.DETERMINISTIC_STEP,
                    Outcome.SUCCEEDED, (System.nanoTime() - start) / 1_000_000, null, null, List.of(),
                    describe(summary)));
            tracker.complete(runId, summary, outcome.warnings());
            log.info("Enrichment run {} for asset {} recorded {} proposal outcome(s); asset status {}",
                    runId, asset.id(), outcome.proposals().size(), outcome.assetStatus());
            return outcome;
        } catch (RuntimeException e) {
            tracker.fail(runId, errorCode(e), errorMessage(e));
            throw e;
        }
    }

    private Asset requireAsset(UUID assetId) {
        return assetRepository.findById(assetId).orElseThrow(() -> new AssetNotFoundException(assetId));
    }

    private MetadataEnrichmentWorkflow requireWorkflow() {
        MetadataEnrichmentWorkflow enrichmentWorkflow = workflow.getIfAvailable();
        if (enrichmentWorkflow == null) {
            throw new CapabilityUnavailableException("AI_DISABLED", "AI enrichment is disabled (ai.enabled=false)");
        }
        enrichmentWorkflow.unavailableReason().ifPresent(reason -> {
            throw new CapabilityUnavailableException("AI_UNAVAILABLE", "AI enrichment is unavailable: " + reason);
        });
        return enrichmentWorkflow;
    }

    private static List<WorkflowStageDefinition> plan(MetadataEnrichmentWorkflow enrichmentWorkflow) {
        List<WorkflowStageDefinition> stages = new ArrayList<>(enrichmentWorkflow.stages());
        stages.add(RECORD_STAGE);
        return stages;
    }

    static RunSummary summary(List<ProposalOutcome> proposals) {
        return new RunSummary(count(proposals, ApprovalRoute.AUTO_APPROVE), count(proposals, ApprovalRoute.HUMAN_REVIEW),
                count(proposals, ApprovalRoute.LOW_CONFIDENCE), count(proposals, ApprovalRoute.REJECTED_BY_POLICY),
                count(proposals, ApprovalRoute.NO_CHANGE));
    }

    private static int count(List<ProposalOutcome> proposals, ApprovalRoute route) {
        return (int) proposals.stream().filter(proposal -> proposal.route() == route).count();
    }

    private static String describe(RunSummary summary) {
        return "%d auto-applied, %d for review, %d low-confidence, %d rejected by policy, %d unchanged".formatted(
                summary.autoApplied(), summary.pendingReview(), summary.lowConfidenceSuggestions(),
                summary.rejectedByPolicy(), summary.unchanged());
    }

    private static String errorCode(RuntimeException e) {
        return e instanceof DamException damException ? damException.code() : "INTERNAL_ERROR";
    }

    private static String errorMessage(RuntimeException e) {
        return e instanceof DamException ? e.getMessage() : "Unexpected error during enrichment";
    }
}

package com.example.dam.metadata.api;

import com.example.dam.metadata.application.enrichment.AgentStepTrace.StepKind;
import com.example.dam.metadata.application.enrichment.EnrichmentRun;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunStatus;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunSummary;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.StageState;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.StageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Schema(description = "Tracked execution of the agentic enrichment workflow with real per-stage status")
public record WorkflowRunResponse(
        UUID workflowId,
        String workflowType,
        UUID assetId,
        String externalAssetId,
        RunStatus status,
        String requestedBy,
        String aiModel,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Long durationMillis,
        @Schema(description = "Names of the stages currently running (parallel stages are joined)") String currentStage,
        List<StageResponse> stages,
        RunSummary summary,
        List<String> warnings,
        RunErrorResponse error) {

    public record StageResponse(String id, String name, StepKind kind, String description, String parallelGroup,
                                boolean conditional, StageStatus status, Instant startedAt, Long durationMillis,
                                Integer inputTokens, Integer outputTokens, List<String> toolCalls,
                                @Schema(description = "Safe one-line outcome summary; never model reasoning")
                                String detail, int attempts) {
        static StageResponse from(StageState stage) {
            return new StageResponse(stage.id(), stage.name(), stage.kind(), stage.description(),
                    stage.parallelGroup(), stage.conditional(), stage.status(), stage.startedAt(),
                    stage.durationMillis(), stage.inputTokens(), stage.outputTokens(), stage.toolCalls(),
                    stage.detail(), stage.attempts());
        }
    }

    public record RunErrorResponse(String code, String message) {
    }

    static WorkflowRunResponse from(EnrichmentRun run) {
        String running = run.runningStages().stream().map(StageState::name).collect(Collectors.joining(", "));
        return new WorkflowRunResponse(run.id(), EnrichmentRun.WORKFLOW_TYPE, run.assetId(), run.externalAssetId(),
                run.status(), run.requestedBy(), run.aiModel(), run.createdAt(), run.startedAt(), run.finishedAt(),
                run.durationMillis(), running.isEmpty() ? null : running,
                run.stages().stream().map(StageResponse::from).toList(), run.summary(), run.warnings(),
                run.errorCode() == null ? null : new RunErrorResponse(run.errorCode(), run.errorMessage()));
    }
}

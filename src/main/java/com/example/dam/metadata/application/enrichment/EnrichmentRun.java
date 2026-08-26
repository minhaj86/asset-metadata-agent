package com.example.dam.metadata.application.enrichment;

import com.example.dam.metadata.application.enrichment.AgentStepTrace.StepKind;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * One tracked execution of the enrichment workflow for an asset: lifecycle, per-stage progress and outcome.
 * Immutable; every transition returns a new instance.
 *
 * @param externalAssetId for display; resolved when the run is created
 */
public record EnrichmentRun(
        UUID id,
        UUID assetId,
        String externalAssetId,
        RunStatus status,
        String requestedBy,
        String aiModel,
        String correlationId,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        List<StageState> stages,
        RunSummary summary,
        List<String> warnings,
        String errorCode,
        String errorMessage) {

    public static final String WORKFLOW_TYPE = "METADATA_ENRICHMENT";

    public enum RunStatus {
        QUEUED, RUNNING, COMPLETED, FAILED;

        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED;
        }
    }

    public enum StageStatus { WAITING, RUNNING, COMPLETED, FAILED, SKIPPED }

    /**
     * @param attempts how often the stage started (retries of malformed AI output increase it)
     */
    public record StageState(String id, String name, StepKind kind, String description, String parallelGroup,
                             boolean conditional, StageStatus status, Instant startedAt, Long durationMillis,
                             Integer inputTokens, Integer outputTokens, List<String> toolCalls, String detail,
                             int attempts) {
        public StageState {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }

        static StageState planned(WorkflowStageDefinition definition) {
            return new StageState(definition.id(), definition.displayName(), definition.kind(),
                    definition.description(), definition.parallelGroup(), definition.conditional(),
                    StageStatus.WAITING, null, null, null, null, List.of(), null, 0);
        }

        StageState started(Instant now) {
            return new StageState(id, name, kind, description, parallelGroup, conditional, StageStatus.RUNNING, now,
                    null, null, null, toolCalls, null, attempts + 1);
        }

        StageState finished(AgentStepTrace step) {
            StageStatus finalStatus = switch (step.outcome()) {
                case SUCCEEDED -> StageStatus.COMPLETED;
                case FAILED -> StageStatus.FAILED;
                case SKIPPED -> StageStatus.SKIPPED;
            };
            return new StageState(id, name, kind == null ? step.kind() : kind, description, parallelGroup, conditional,
                    finalStatus, startedAt, step.outcome() == AgentStepTrace.Outcome.SKIPPED ? null : step.durationMillis(),
                    step.inputTokens(), step.outputTokens(), step.toolCalls(), step.detail(), attempts);
        }

        StageState closed(StageStatus closingStatus, String closingDetail) {
            return new StageState(id, name, kind, description, parallelGroup, conditional, closingStatus, startedAt,
                    durationMillis, inputTokens, outputTokens, toolCalls, closingDetail == null ? detail : closingDetail,
                    attempts);
        }
    }

    public record RunSummary(int autoApplied, int pendingReview, int lowConfidenceSuggestions, int rejectedByPolicy,
                             int unchanged) {
        public int total() {
            return autoApplied + pendingReview + lowConfidenceSuggestions + rejectedByPolicy + unchanged;
        }
    }

    public EnrichmentRun {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(createdAt, "createdAt");
        stages = List.copyOf(stages);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static EnrichmentRun create(UUID id, UUID assetId, String externalAssetId, String requestedBy,
                                       String aiModel, String correlationId, List<WorkflowStageDefinition> plan,
                                       Instant now) {
        return new EnrichmentRun(id, assetId, externalAssetId, RunStatus.QUEUED, requestedBy, aiModel, correlationId,
                now, null, null, plan.stream().map(StageState::planned).toList(), null, List.of(), null, null);
    }

    public EnrichmentRun running(Instant now) {
        return with(RunStatus.RUNNING, now, null, stages, summary, warnings, null, null);
    }

    public EnrichmentRun stageStarted(String stageId, Instant now) {
        return withStages(replaceStage(stageId, stage -> stage.started(now)));
    }

    public EnrichmentRun stageFinished(AgentStepTrace step) {
        return withStages(replaceStage(step.name(), stage -> stage.finished(step)));
    }

    public EnrichmentRun completed(RunSummary runSummary, List<String> runWarnings, Instant now) {
        List<StageState> closed = stages.stream().map(stage -> switch (stage.status()) {
            case WAITING -> stage.closed(StageStatus.SKIPPED, stage.conditional() ? "Condition not met" : "Not required");
            case RUNNING -> stage.closed(StageStatus.COMPLETED, null);
            default -> stage;
        }).toList();
        return with(RunStatus.COMPLETED, startedAt, now, closed, runSummary, runWarnings, null, null);
    }

    public EnrichmentRun failed(String code, String message, Instant now) {
        List<StageState> closed = stages.stream().map(stage -> switch (stage.status()) {
            case WAITING -> stage.closed(StageStatus.SKIPPED, "Not reached");
            case RUNNING -> stage.closed(StageStatus.FAILED, message);
            default -> stage;
        }).toList();
        return with(RunStatus.FAILED, startedAt, now, closed, summary, warnings, code, message);
    }

    /** Wall-clock duration of a finished run, {@code null} while it is queued or running. */
    public Long durationMillis() {
        return startedAt == null || finishedAt == null ? null : Duration.between(startedAt, finishedAt).toMillis();
    }

    public List<StageState> runningStages() {
        return stages.stream().filter(stage -> stage.status() == StageStatus.RUNNING).toList();
    }

    private List<StageState> replaceStage(String stageId, UnaryOperator<StageState> change) {
        List<StageState> updated = new ArrayList<>(stages);
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).id().equals(stageId)) {
                updated.set(i, change.apply(updated.get(i)));
                return updated;
            }
        }
        // Stage not in the published plan (e.g. a framework-internal step): track it rather than drop it.
        StageState unplanned = new StageState(stageId, stageId, null, "", null, false, StageStatus.WAITING, null, null,
                null, null, List.of(), null, 0);
        updated.add(change.apply(unplanned));
        return updated;
    }

    private EnrichmentRun withStages(List<StageState> newStages) {
        return with(status, startedAt, finishedAt, newStages, summary, warnings, errorCode, errorMessage);
    }

    private EnrichmentRun with(RunStatus newStatus, Instant newStartedAt, Instant newFinishedAt,
                               List<StageState> newStages, RunSummary newSummary, List<String> newWarnings,
                               String newErrorCode, String newErrorMessage) {
        return new EnrichmentRun(id, assetId, externalAssetId, newStatus, requestedBy, aiModel, correlationId,
                createdAt, newStartedAt, newFinishedAt, newStages, newSummary, newWarnings, newErrorCode,
                newErrorMessage);
    }
}

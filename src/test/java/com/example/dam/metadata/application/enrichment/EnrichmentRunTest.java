package com.example.dam.metadata.application.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.metadata.application.enrichment.AgentStepTrace.Outcome;
import com.example.dam.metadata.application.enrichment.AgentStepTrace.StepKind;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunStatus;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunSummary;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.StageState;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.StageStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrichmentRunTest {

    private static final Instant T0 = Instant.parse("2026-10-03T10:00:00Z");
    private static final List<WorkflowStageDefinition> PLAN = List.of(
            new WorkflowStageDefinition("extract", "Extraction", StepKind.AI_AGENT, "", null, false),
            new WorkflowStageDefinition("taxonomy", "Taxonomy", StepKind.AI_AGENT, "", "parallel", true),
            new WorkflowStageDefinition("enrich", "Enrichment", StepKind.AI_AGENT, "", "parallel", false),
            new WorkflowStageDefinition("route", "Routing", StepKind.DETERMINISTIC_STEP, "", null, true));

    @Test
    void stagesProgressFromWaitingThroughRunningToTheirOutcome() {
        EnrichmentRun run = newRun().running(T0).stageStarted("extract", T0.plusMillis(5));
        assertThat(stage(run, "extract").status()).isEqualTo(StageStatus.RUNNING);
        assertThat(stage(run, "taxonomy").status()).isEqualTo(StageStatus.WAITING);

        run = run.stageFinished(new AgentStepTrace("extract", StepKind.AI_AGENT, Outcome.SUCCEEDED, 120, 900, 80,
                List.of("getProductInformation"), "6 candidate(s)"));
        StageState extract = stage(run, "extract");
        assertThat(extract.status()).isEqualTo(StageStatus.COMPLETED);
        assertThat(extract.durationMillis()).isEqualTo(120);
        assertThat(extract.toolCalls()).containsExactly("getProductInformation");
        assertThat(extract.attempts()).isEqualTo(1);
    }

    @Test
    void parallelStagesCanRunAtTheSameTimeAndRetriesCountAttempts() {
        EnrichmentRun run = newRun().running(T0)
                .stageStarted("taxonomy", T0)
                .stageStarted("enrich", T0);
        assertThat(run.runningStages()).extracting(StageState::id).containsExactly("taxonomy", "enrich");

        run = run.stageFinished(new AgentStepTrace("enrich", StepKind.AI_AGENT, Outcome.FAILED, 10, null, null,
                List.of(), "OutputParsingException")).stageStarted("enrich", T0.plusMillis(20));
        assertThat(stage(run, "enrich").status()).isEqualTo(StageStatus.RUNNING);
        assertThat(stage(run, "enrich").attempts()).isEqualTo(2);
    }

    @Test
    void completionSkipsStagesThatNeverRan() {
        EnrichmentRun run = newRun().running(T0).completed(new RunSummary(2, 1, 0, 0, 3), List.of(), T0.plusSeconds(4));

        assertThat(run.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.durationMillis()).isEqualTo(4000);
        assertThat(stage(run, "taxonomy").status()).isEqualTo(StageStatus.SKIPPED);
        assertThat(stage(run, "taxonomy").detail()).isEqualTo("Condition not met");
        assertThat(stage(run, "extract").detail()).isEqualTo("Not required");
        assertThat(run.summary().total()).isEqualTo(6);
    }

    @Test
    void failureMarksRunningStagesFailedAndTheRestSkipped() {
        EnrichmentRun run = newRun().running(T0).stageStarted("extract", T0)
                .failed("AI_TIMEOUT", "Enrichment did not finish in time", T0.plusSeconds(180));

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo("AI_TIMEOUT");
        assertThat(stage(run, "extract").status()).isEqualTo(StageStatus.FAILED);
        assertThat(stage(run, "route").status()).isEqualTo(StageStatus.SKIPPED);
        assertThat(run.status().isTerminal()).isTrue();
    }

    private static EnrichmentRun newRun() {
        EnrichmentRun run = EnrichmentRun.create(UUID.randomUUID(), UUID.randomUUID(), "DA-1", "jane", "demo:model",
                null, PLAN, T0);
        assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(run.stages()).allMatch(stage -> stage.status() == StageStatus.WAITING);
        return run;
    }

    private static StageState stage(EnrichmentRun run, String id) {
        return run.stages().stream().filter(stage -> stage.id().equals(id)).findFirst().orElseThrow();
    }
}

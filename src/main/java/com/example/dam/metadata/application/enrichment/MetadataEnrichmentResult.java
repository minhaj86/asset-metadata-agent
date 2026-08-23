package com.example.dam.metadata.application.enrichment;

import com.example.dam.metadata.application.quality.SemanticQualityAnalyzer.SemanticQualityAssessment;
import java.util.List;
import java.util.UUID;

/**
 * Output of the enrichment workflow: typed proposals with planned routes, never applied changes.
 *
 * @param qualityReview semantic review of the combined metadata, {@code null} if skipped or unavailable
 * @param warnings      degraded steps and discarded suggestions
 */
public record MetadataEnrichmentResult(
        UUID workflowRunId,
        UUID assetId,
        List<PlannedProposal> proposals,
        TaxonomySuggestion taxonomy,
        SemanticQualityAssessment qualityReview,
        List<AgentStepTrace> steps,
        List<String> warnings,
        String aiModel) {

    public MetadataEnrichmentResult {
        proposals = List.copyOf(proposals);
        steps = List.copyOf(steps);
        warnings = List.copyOf(warnings);
    }

    /** True when at least one AI agent ran and every AI agent that ran failed. */
    public boolean allAiAgentsFailed() {
        List<AgentStepTrace> aiSteps = steps.stream()
                .filter(step -> step.kind() == AgentStepTrace.StepKind.AI_AGENT)
                .filter(step -> step.outcome() != AgentStepTrace.Outcome.SKIPPED)
                .toList();
        return !aiSteps.isEmpty() && aiSteps.stream().allMatch(step -> step.outcome() == AgentStepTrace.Outcome.FAILED);
    }
}

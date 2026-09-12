package com.example.dam.ai.workflow;

import com.example.dam.ai.agent.QualityReviewOutput;
import com.example.dam.ai.config.AiModelInfo;
import com.example.dam.ai.observability.AgentObservabilityListener;
import com.example.dam.ai.observability.WorkflowTrace;
import com.example.dam.ai.quality.QualityReviewMapper;
import com.example.dam.metadata.application.enrichment.MetadataEnrichmentResult;
import com.example.dam.metadata.application.enrichment.PlannedProposal;
import com.example.dam.metadata.application.enrichment.TaxonomySuggestion;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.List;
import java.util.UUID;

/** Builds the workflow's typed result from the shared agentic scope. */
final class EnrichmentResultAssembler {

    private final AiModelInfo modelInfo;

    EnrichmentResultAssembler(AiModelInfo modelInfo) {
        this.modelInfo = modelInfo;
    }

    MetadataEnrichmentResult assemble(AgenticScope scope) {
        WorkflowTrace trace = scope.readState(AgentObservabilityListener.TRACE_KEY) instanceof WorkflowTrace t
                ? t : new WorkflowTrace();
        ApprovalPlan plan = scope.readState(EnrichmentState.Plan.class);
        CandidateSet candidates = scope.readState(EnrichmentState.Candidates.class);
        QualityReviewOutput qualityReview = scope.readState(EnrichmentState.QualityReview.class);
        List<PlannedProposal> proposals = plan == null ? List.of() : plan.proposals();
        TaxonomySuggestion taxonomy = candidates == null ? null : candidates.taxonomy();
        return new MetadataEnrichmentResult(
                (UUID) scope.readState(EnrichmentState.WORKFLOW_RUN_ID),
                (UUID) scope.readState(EnrichmentState.ASSET_ID),
                proposals,
                taxonomy,
                QualityReviewMapper.toAssessment(qualityReview, modelInfo.chatModelLabel()),
                trace.steps(),
                trace.warnings(),
                modelInfo.chatModelLabel());
    }
}

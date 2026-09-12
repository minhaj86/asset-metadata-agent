package com.example.dam.ai.workflow.step;

import com.example.dam.ai.agent.EnrichmentOutput;
import com.example.dam.ai.agent.ExtractionOutput;
import com.example.dam.ai.agent.PromptContextRenderer;
import com.example.dam.ai.agent.TaxonomyClassification;
import com.example.dam.ai.observability.AgentObservabilityListener;
import com.example.dam.ai.observability.WorkflowTrace;
import com.example.dam.ai.workflow.CandidateConsolidator;
import com.example.dam.ai.workflow.CandidateSet;
import com.example.dam.ai.workflow.EnrichmentState;
import com.example.dam.metadata.application.enrichment.AssetEnrichmentContext;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * Deterministic step: merges extraction, taxonomy and enrichment outputs into typed, schema-checked candidates
 * (invented taxonomy nodes are rejected here) and renders them for the quality review.
 */
public class ConsolidateProposalsStep {

    private final CandidateConsolidator consolidator;
    private final PromptContextRenderer renderer;

    public ConsolidateProposalsStep(CandidateConsolidator consolidator, PromptContextRenderer renderer) {
        this.consolidator = consolidator;
        this.renderer = renderer;
    }

    @Agent(name = "consolidateProposals", description = "Merges and validates agent outputs into typed candidates",
            typedOutputKey = EnrichmentState.Candidates.class)
    public CandidateSet consolidate(@K(EnrichmentState.Context.class) AssetEnrichmentContext context,
                                    @K(EnrichmentState.Extraction.class) ExtractionOutput extraction,
                                    @K(EnrichmentState.Enrichment.class) EnrichmentOutput enrichment,
                                    AgenticScope scope) {
        TaxonomyClassification taxonomy = scope.readState(EnrichmentState.Taxonomy.class);
        CandidateSet candidates = consolidator.consolidate(context, extraction, enrichment, taxonomy);
        scope.writeState(EnrichmentState.METADATA_TO_REVIEW,
                renderer.metadataToReview(context.schema(), context.currentMetadata(), candidates.candidates()));
        if (scope.readState(AgentObservabilityListener.TRACE_KEY) instanceof WorkflowTrace trace) {
            candidates.warnings().forEach(trace::warn);
        }
        return candidates;
    }
}

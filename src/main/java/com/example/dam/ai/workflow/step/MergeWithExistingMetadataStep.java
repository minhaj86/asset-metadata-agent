package com.example.dam.ai.workflow.step;

import com.example.dam.ai.agent.ExtractionOutput;
import com.example.dam.ai.agent.PromptContextRenderer;
import com.example.dam.ai.workflow.CandidateConsolidator;
import com.example.dam.ai.workflow.EnrichmentState;
import com.example.dam.metadata.application.enrichment.AssetEnrichmentContext;
import com.example.dam.metadata.application.enrichment.MetadataCandidate;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic step "Existing metadata": overlays the extraction candidates on the stored metadata so that the
 * taxonomy and enrichment agents reason over one consistent working view.
 */
public class MergeWithExistingMetadataStep {

    private final CandidateConsolidator consolidator;
    private final PromptContextRenderer renderer;

    public MergeWithExistingMetadataStep(CandidateConsolidator consolidator, PromptContextRenderer renderer) {
        this.consolidator = consolidator;
        this.renderer = renderer;
    }

    @Agent(name = "mergeWithExistingMetadata", description = "Overlays extraction candidates on existing metadata",
            outputKey = EnrichmentState.WORKING_METADATA)
    public String merge(@K(EnrichmentState.Context.class) AssetEnrichmentContext context,
                        @K(EnrichmentState.Extraction.class) ExtractionOutput extraction) {
        List<MetadataCandidate> extracted = consolidator.fromSuggestions(context, extraction.candidates(),
                CandidateConsolidator.EXTRACTION_AGENT, new ArrayList<>());
        return renderer.workingMetadata(context.currentMetadata(), extracted);
    }
}

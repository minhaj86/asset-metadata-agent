package com.example.dam.ai.workflow.step;

import com.example.dam.ai.agent.PromptContextRenderer;
import com.example.dam.ai.observability.AgentObservabilityListener;
import com.example.dam.ai.observability.WorkflowTrace;
import com.example.dam.ai.rag.MetadataKnowledgeService;
import com.example.dam.ai.workflow.EnrichmentState;
import com.example.dam.metadata.application.MetadataValidationService;
import com.example.dam.metadata.application.enrichment.AssetEnrichmentContext;
import com.example.dam.metadata.application.enrichment.EnrichmentContextAssembler;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.V;
import java.util.UUID;

/**
 * Deterministic step "Load asset context": assembles asset, metadata, schema, DAM, product and vision context,
 * retrieves governance guidance (RAG) and publishes prompt-ready renderings into the shared scope.
 */
public class LoadAssetContextStep {

    private final EnrichmentContextAssembler assembler;
    private final MetadataKnowledgeService knowledgeService;
    private final MetadataValidationService validationService;
    private final PromptContextRenderer renderer;

    public LoadAssetContextStep(EnrichmentContextAssembler assembler, MetadataKnowledgeService knowledgeService,
                                MetadataValidationService validationService, PromptContextRenderer renderer) {
        this.assembler = assembler;
        this.knowledgeService = knowledgeService;
        this.validationService = validationService;
        this.renderer = renderer;
    }

    @Agent(name = "loadAssetContext", description = "Loads asset, metadata, schema, DAM, product and vision context",
            typedOutputKey = EnrichmentState.Context.class)
    public AssetEnrichmentContext load(@V(EnrichmentState.ASSET_ID) UUID assetId, AgenticScope scope) {
        AssetEnrichmentContext context = assembler.assemble(assetId);
        String guidance = knowledgeService.guidanceFor(context);
        scope.writeState(EnrichmentState.ASSET_CONTEXT, renderer.assetContext(context));
        scope.writeState(EnrichmentState.CURRENT_METADATA, renderer.currentMetadata(context.currentMetadata()));
        scope.writeState(EnrichmentState.FIELD_CATALOG, renderer.fieldCatalog(context.schema()));
        scope.writeState(EnrichmentState.TAXONOMY_CANDIDATES, renderer.taxonomyCandidates(context.taxonomyCandidates()));
        scope.writeState(EnrichmentState.GOVERNANCE_GUIDANCE,
                guidance.isBlank() ? "No governance guidance available." : guidance);
        scope.writeState(EnrichmentState.DETERMINISTIC_FINDINGS,
                renderer.findings(validationService.validate(context.asset()).report().issues()));
        if (scope.readState(AgentObservabilityListener.TRACE_KEY) instanceof WorkflowTrace trace) {
            context.warnings().forEach(trace::warn);
            if (guidance.isBlank()) {
                trace.warn("Governance knowledge unavailable; agents run without retrieved guidance");
            }
        }
        return context;
    }
}

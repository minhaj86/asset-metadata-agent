package com.example.dam.ai.agent;

import com.example.dam.metadata.application.enrichment.MetadataEnrichmentResult;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import java.util.UUID;

/**
 * Typed entry point of the enrichment workflow. Implemented by LangChain4j Agentic as a deterministic
 * orchestrator (sequence, parallel and conditional sub-workflows over a shared agentic scope); see
 * {@code AgenticMetadataEnrichmentWorkflow}.
 */
public interface MetadataSupervisorAgent {

    @Agent(name = "metadataSupervisor", description = "Orchestrates metadata enrichment of one asset")
    MetadataEnrichmentResult enrich(@V("assetId") UUID assetId, @V("workflowRunId") UUID workflowRunId);
}

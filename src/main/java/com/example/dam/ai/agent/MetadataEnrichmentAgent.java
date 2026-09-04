package com.example.dam.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

/** Proposes missing or improved descriptive metadata based on existing metadata, product data and governance. */
public interface MetadataEnrichmentAgent {

    @Agent(name = "metadataEnrichmentAgent",
            description = "Proposes missing or improved descriptive metadata such as title, alt text and keywords")
    EnrichmentOutput enrich(@V("assetContext") String assetContext,
                            @V("workingMetadata") String workingMetadata,
                            @V("fieldCatalog") String fieldCatalog,
                            @V("governanceGuidance") String governanceGuidance);
}

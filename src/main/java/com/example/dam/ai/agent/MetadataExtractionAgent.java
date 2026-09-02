package com.example.dam.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

/** Extracts metadata candidates from supplied, textual asset information. Proposes; never persists. */
public interface MetadataExtractionAgent {

    @Agent(name = "metadataExtractionAgent",
            description = "Extracts metadata candidates from filename, embedded metadata, image analysis and product data")
    ExtractionOutput extract(@V("assetContext") String assetContext,
                             @V("currentMetadata") String currentMetadata,
                             @V("fieldCatalog") String fieldCatalog);
}

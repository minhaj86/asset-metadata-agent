package com.example.dam.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

/** Maps an asset to an existing taxonomy node retrieved via the taxonomy tool; never invents nodes. */
public interface TaxonomyAgent {

    @Agent(name = "taxonomyAgent", description = "Maps asset metadata to an existing node of the product taxonomy")
    TaxonomyClassification classify(@V("assetContext") String assetContext,
                                    @V("workingMetadata") String workingMetadata,
                                    @V("taxonomyCandidates") String taxonomyCandidates);
}

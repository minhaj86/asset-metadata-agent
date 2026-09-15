package com.example.dam.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

/** Translates natural language into a structured search intent. Search execution stays deterministic. */
public interface SearchIntentAgent {

    @Agent(name = "searchIntentAgent",
            description = "Translates a natural-language asset query into a structured search intent")
    SearchIntentOutput interpret(@V("query") String query,
                                 @V("searchableFields") String searchableFields,
                                 @V("assetTypes") String assetTypes);
}

package com.example.dam.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

/** Reviews soft, semantic metadata quality. Deterministic findings are supplied and must not be repeated. */
public interface MetadataQualityAgent {

    @Agent(name = "metadataQualityAgent",
            description = "Reviews semantic metadata quality: weak descriptions, poor alt text, irrelevant keywords, "
                    + "ambiguous classification and inconsistent semantics")
    QualityReviewOutput review(@V("metadataToReview") String metadataToReview,
                               @V("deterministicFindings") String deterministicFindings,
                               @V("governanceGuidance") String governanceGuidance);
}

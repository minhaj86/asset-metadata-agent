package com.example.dam.ai.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/**
 * One field suggestion as returned by an LLM (structured output). Validated and typed by Java afterwards.
 * All properties are required in the JSON schema so that grammar-constrained providers (e.g. Ollama) cannot omit
 * the confidence, which would otherwise silently default to 0.0.
 */
public record FieldSuggestion(
        @Description("Metadata field key exactly as listed in the field catalog, e.g. color")
        @JsonProperty(required = true) String field,
        @Description("Proposed value(s). Exactly one element for single-valued fields. Use controlled-vocabulary "
                + "values verbatim.") @JsonProperty(required = true) List<String> values,
        @Description("Probability between 0.0 and 1.0 that the value is correct")
        @JsonProperty(required = true) double confidence,
        @Description("One sentence explaining why this value is proposed")
        @JsonProperty(required = true) String explanation,
        @Description("Concrete evidence references, e.g. imageAnalysis.orientation=front or product.color=Light Blue")
        @JsonProperty(required = true) List<String> evidence) {
}

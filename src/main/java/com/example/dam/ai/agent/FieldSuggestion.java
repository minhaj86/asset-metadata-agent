package com.example.dam.ai.agent;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/** One field suggestion as returned by an LLM (structured output). Validated and typed by Java afterwards. */
public record FieldSuggestion(
        @Description("Metadata field key exactly as listed in the field catalog, e.g. color") String field,
        @Description("Proposed value(s). Exactly one element for single-valued fields. Use controlled-vocabulary "
                + "values verbatim.") List<String> values,
        @Description("Probability between 0.0 and 1.0 that the value is correct") double confidence,
        @Description("One sentence explaining why this value is proposed") String explanation,
        @Description("Concrete evidence references, e.g. imageAnalysis.orientation=front or product.color=Light Blue")
        List<String> evidence) {
}

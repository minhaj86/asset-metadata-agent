package com.example.dam.ai.agent;

import com.example.dam.ai.observability.StepOutcome;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/** Structured output of the {@link MetadataQualityAgent}: soft, semantic findings only. */
public record QualityReviewOutput(
        @Description("Semantic quality findings; empty if none")
        @JsonProperty(required = true) List<QualityFinding> findings,
        @Description("Probability between 0.0 and 1.0 that the findings are accurate")
        @JsonProperty(required = true) double confidence,
        @Description("One or two sentences summarising metadata quality")
        @JsonProperty(required = true) String summary) implements StepOutcome {

    @Override
    public String outcomeSummary() {
        return summary;
    }

    public record QualityFinding(
            @Description("Affected field key") @JsonProperty(required = true) String field,
            @Description("One of WEAK_DESCRIPTION, POOR_ALT_TEXT, IRRELEVANT_KEYWORDS, AMBIGUOUS_CLASSIFICATION, "
                    + "INCONSISTENT_SEMANTICS") @JsonProperty(required = true) String category,
            @Description("LOW, MEDIUM or HIGH") @JsonProperty(required = true) String severity,
            @Description("What is wrong") @JsonProperty(required = true) String message,
            @Description("Concrete improvement suggestion")
            @JsonProperty(required = true) String suggestedImprovement) {
    }
}

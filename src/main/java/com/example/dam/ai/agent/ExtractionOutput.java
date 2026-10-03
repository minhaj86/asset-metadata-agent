package com.example.dam.ai.agent;

import com.example.dam.ai.observability.StepOutcome;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/** Structured output of the {@link MetadataExtractionAgent}. */
public record ExtractionOutput(
        @Description("Metadata candidates directly supported by the supplied evidence; empty if none")
        @JsonProperty(required = true) List<FieldSuggestion> candidates,
        @Description("Short summary of how the evidence was interpreted")
        @JsonProperty(required = true) String reasoningSummary)
        implements StepOutcome {

    public static ExtractionOutput unavailable(String reason) {
        return new ExtractionOutput(List.of(), "Extraction unavailable: " + reason);
    }

    @Override
    public String outcomeSummary() {
        return "%d candidate(s): %s".formatted(candidates == null ? 0 : candidates.size(), reasoningSummary);
    }
}

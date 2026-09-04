package com.example.dam.ai.agent;

import com.example.dam.ai.observability.StepOutcome;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/** Structured output of the {@link MetadataEnrichmentAgent}. */
public record EnrichmentOutput(
        @Description("Proposals for missing or improvable fields; empty if evidence is insufficient")
        List<FieldSuggestion> proposals,
        @Description("Short summary of the enrichment reasoning") String reasoningSummary) implements StepOutcome {

    public static EnrichmentOutput unavailable(String reason) {
        return new EnrichmentOutput(List.of(), "Enrichment unavailable: " + reason);
    }

    @Override
    public String outcomeSummary() {
        return "%d proposal(s): %s".formatted(proposals == null ? 0 : proposals.size(), reasoningSummary);
    }
}

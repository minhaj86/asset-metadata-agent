package com.example.dam.ai.agent;

import com.example.dam.ai.observability.StepOutcome;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/** Structured output of the {@link TaxonomyAgent}. */
public record TaxonomyClassification(
        @Description("Id of an existing taxonomy node returned by searchTaxonomy, or null if none fits")
        String taxonomyNodeId,
        @Description("Path of that node exactly as returned by the tool") String taxonomyPath,
        @Description("Probability between 0.0 and 1.0 that the node is correct") double confidence,
        @Description("Why this node fits") String reason,
        @Description("Evidence used, e.g. product.department=Men") List<String> evidence) implements StepOutcome {

    @Override
    public String outcomeSummary() {
        return taxonomyNodeId == null ? "no node proposed" : "%s (%.2f)".formatted(taxonomyPath, confidence);
    }
}

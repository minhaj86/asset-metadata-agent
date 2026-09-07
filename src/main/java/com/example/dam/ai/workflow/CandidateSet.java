package com.example.dam.ai.workflow;

import com.example.dam.ai.observability.StepOutcome;
import com.example.dam.metadata.application.enrichment.MetadataCandidate;
import com.example.dam.metadata.application.enrichment.TaxonomySuggestion;
import java.util.List;

/** Typed, de-duplicated candidates of one run plus discarded-suggestion warnings. */
public record CandidateSet(List<MetadataCandidate> candidates, TaxonomySuggestion taxonomy, List<String> warnings)
        implements StepOutcome {

    public CandidateSet {
        candidates = List.copyOf(candidates);
        warnings = List.copyOf(warnings);
    }

    @Override
    public String outcomeSummary() {
        return "%d candidate(s), %d discarded suggestion(s)".formatted(candidates.size(), warnings.size());
    }
}

package com.example.dam.ai.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/** Structured output of the {@link SearchIntentAgent}. Interpretation only; Java validates and executes it. */
public record SearchIntentOutput(
        @Description("Exact-match filters on searchable fields using vocabulary values verbatim")
        @JsonProperty(required = true) List<FilterCondition> filters,
        @Description("Remaining descriptive words for semantic ranking, or null if nothing descriptive remains")
        @JsonProperty(required = false) String semanticQuery,
        @Description("Days from today within which usage rights expire, or null if not asked")
        @JsonProperty(required = false) Integer rightsExpiringWithinDays,
        @Description("Field keys that must be missing, e.g. copyrightOwner")
        @JsonProperty(required = true) List<String> missingFields,
        @Description("Asset type constant if the query restricts it, otherwise null")
        @JsonProperty(required = false) String assetType,
        @Description("External asset id the results should be similar to, otherwise null")
        @JsonProperty(required = false) String similarToExternalAssetId,
        @Description("Short explanation of the interpretation")
        @JsonProperty(required = true) String interpretationNotes) {

    public record FilterCondition(@Description("Searchable field key") @JsonProperty(required = true) String field,
                                  @Description("Value; vocabulary value verbatim where a vocabulary exists")
                                  @JsonProperty(required = true) String value) {
    }
}

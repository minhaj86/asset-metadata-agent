package com.example.dam.ai.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/** Structured output of the knowledge assistant. */
public record KnowledgeAnswerOutput(
        @Description("Answer based only on the supplied knowledge; 'UNKNOWN' if it is not covered")
        @JsonProperty(required = true) String answer,
        @Description("true if the answer is supported by the supplied knowledge")
        @JsonProperty(required = true) boolean grounded,
        @Description("Titles/sections of the knowledge used")
        @JsonProperty(required = true) List<String> citedSources) {
}

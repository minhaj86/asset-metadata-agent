package com.example.dam.ai.agent;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/** Structured output of the knowledge assistant. */
public record KnowledgeAnswerOutput(
        @Description("Answer based only on the supplied knowledge; 'UNKNOWN' if it is not covered") String answer,
        @Description("true if the answer is supported by the supplied knowledge") boolean grounded,
        @Description("Titles/sections of the knowledge used") List<String> citedSources) {
}

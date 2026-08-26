package com.example.dam.metadata.application.enrichment;

import java.util.List;

/** Execution trace of one workflow step, exposed to API clients for transparency. */
public record AgentStepTrace(
        String name,
        StepKind kind,
        Outcome outcome,
        long durationMillis,
        Integer inputTokens,
        Integer outputTokens,
        List<String> toolCalls,
        String detail) {

    public enum StepKind { AI_AGENT, DETERMINISTIC_STEP }

    public enum Outcome { SUCCEEDED, FAILED, SKIPPED }

    public AgentStepTrace {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}

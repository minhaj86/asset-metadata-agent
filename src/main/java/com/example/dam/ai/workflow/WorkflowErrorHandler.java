package com.example.dam.ai.workflow;

import com.example.dam.ai.agent.EnrichmentOutput;
import com.example.dam.ai.agent.ExtractionOutput;
import com.example.dam.ai.observability.AgentObservabilityListener;
import com.example.dam.ai.observability.WorkflowTrace;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.service.output.OutputParsingException;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resilience policy of the enrichment workflow. AI agents degrade gracefully: malformed output is retried once,
 * then the agent's contribution is replaced by an empty fallback so the run continues with the remaining agents.
 * Failures of deterministic steps (e.g. asset not found, database errors) abort the run.
 */
final class WorkflowErrorHandler implements Function<ErrorContext, ErrorRecoveryResult> {

    private static final Logger log = LoggerFactory.getLogger(WorkflowErrorHandler.class);
    private static final int MAX_RETRIES = 1;

    private final Map<String, Function<String, Object>> fallbacks = Map.of(
            CandidateConsolidator.EXTRACTION_AGENT, ExtractionOutput::unavailable,
            CandidateConsolidator.ENRICHMENT_AGENT, EnrichmentOutput::unavailable,
            CandidateConsolidator.TAXONOMY_AGENT, reason -> null,
            "metadataQualityAgent", reason -> null);

    @Override
    public ErrorRecoveryResult apply(ErrorContext context) {
        String agent = context.agentName();
        if (!fallbacks.containsKey(agent)) {
            return ErrorRecoveryResult.throwException();
        }
        Throwable cause = rootCause(context.exception());
        WorkflowTrace trace = context.agenticScope().readState(AgentObservabilityListener.TRACE_KEY) instanceof WorkflowTrace t
                ? t : new WorkflowTrace();
        if (isMalformedOutput(context.exception()) && trace.incrementRetries(agent) <= MAX_RETRIES) {
            log.warn("Agent {} returned malformed output; retrying once", agent);
            return ErrorRecoveryResult.retry();
        }
        String reason = cause.getClass().getSimpleName();
        trace.warn("%s unavailable (%s); the run continued without its contribution".formatted(agent, reason));
        return ErrorRecoveryResult.result(fallbacks.get(agent).apply(reason));
    }

    private static boolean isMalformedOutput(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof OutputParsingException
                    || current.getClass().getName().startsWith("com.fasterxml.jackson.core")) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}

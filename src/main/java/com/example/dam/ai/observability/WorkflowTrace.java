package com.example.dam.ai.observability;

import com.example.dam.metadata.application.enrichment.AgentStepTrace;
import com.example.dam.metadata.application.enrichment.AgentStepTrace.Outcome;
import com.example.dam.metadata.application.enrichment.AgentStepTrace.StepKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-workflow-run execution trace, kept in the agentic scope and filled by {@link AgentObservabilityListener}.
 * Thread-safe because parallel agents report concurrently. Every finished or skipped step is also forwarded to an
 * optional sink (live progress of the run).
 */
public final class WorkflowTrace {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTrace.class);

    private final List<AgentStepTrace> steps = new CopyOnWriteArrayList<>();
    private final Map<String, List<String>> toolCalls = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> retries = new ConcurrentHashMap<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final Consumer<AgentStepTrace> sink;

    public WorkflowTrace() {
        this(step -> {
        });
    }

    public WorkflowTrace(Consumer<AgentStepTrace> sink) {
        this.sink = sink;
    }

    public void recordToolCall(String agent, String tool, boolean failed) {
        toolCalls.computeIfAbsent(agent, key -> new CopyOnWriteArrayList<>()).add(failed ? tool + " (failed)" : tool);
    }

    public void recordStep(String agent, StepKind kind, Outcome outcome, long durationMillis, Integer inputTokens,
                           Integer outputTokens, String detail) {
        record(new AgentStepTrace(agent, kind, outcome, durationMillis, inputTokens, outputTokens,
                toolCalls.getOrDefault(agent, List.of()), detail));
    }

    public void recordSkipped(String agent, StepKind kind, String reason) {
        record(new AgentStepTrace(agent, kind, Outcome.SKIPPED, 0, null, null, List.of(), reason));
    }

    private void record(AgentStepTrace step) {
        steps.add(step);
        try {
            sink.accept(step);
        } catch (RuntimeException e) {
            log.warn("Progress sink failed for step {}: {}", step.name(), e.toString());
        }
    }

    public void warn(String warning) {
        warnings.add(warning);
    }

    public int incrementRetries(String agent) {
        return retries.computeIfAbsent(agent, key -> new AtomicInteger()).incrementAndGet();
    }

    public List<AgentStepTrace> steps() {
        return new ArrayList<>(steps);
    }

    public List<String> warnings() {
        return new ArrayList<>(warnings);
    }
}

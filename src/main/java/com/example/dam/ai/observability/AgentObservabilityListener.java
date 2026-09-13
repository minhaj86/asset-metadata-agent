package com.example.dam.ai.observability;

import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.common.observability.CorrelationContext;
import com.example.dam.metadata.application.enrichment.AgentStepTrace.Outcome;
import com.example.dam.metadata.application.enrichment.AgentStepTrace.StepKind;
import com.example.dam.metadata.application.enrichment.EnrichmentProgressListener;
import dev.langchain4j.agentic.observability.AfterAgentToolExecution;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * LangChain4j Agentic listener capturing agent name, duration, model, token usage, tool calls, outcome,
 * asset id and workflow correlation id. Registered on the workflow root and inherited by all sub-agents.
 * Meters: {@code dam.ai.agent.invocations} (timer) and {@code dam.ai.tool.calls} (counter).
 * Prompts and tool arguments are deliberately not logged.
 */
@Component
@ConditionalOnAiEnabled
public class AgentObservabilityListener implements AgentListener {

    /** Scope key under which workflows keep their {@link WorkflowTrace}. */
    public static final String TRACE_KEY = "workflowTrace";
    /** Scope key for the request correlation id (thread-independent, unlike MDC). */
    public static final String CORRELATION_KEY = "correlationId";
    private static final String WORKFLOW_RUN_KEY = "workflowRunId";
    private static final String ASSET_KEY = "assetId";

    private static final Logger log = LoggerFactory.getLogger(AgentObservabilityListener.class);

    private final MeterRegistry registry;
    private final EnrichmentProgressListener progress;
    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();

    public AgentObservabilityListener(MeterRegistry registry, EnrichmentProgressListener progress) {
        this.registry = registry;
        this.progress = progress;
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        if (isStep(request.agent())) {
            startTimes.put(key(request.agenticScope(), request.agent()), System.nanoTime());
            if (request.agenticScope() != null
                    && request.agenticScope().readState(WORKFLOW_RUN_KEY) instanceof UUID runId) {
                progress.stageStarted(runId, request.agent().name());
            }
        }
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        AgentInstance agent = response.agent();
        if (!isStep(agent)) {
            return;
        }
        long millis = elapsedMillis(response.agenticScope(), agent);
        ChatResponse chatResponse = response.chatResponse();
        Integer inputTokens = chatResponse != null && chatResponse.tokenUsage() != null
                ? chatResponse.tokenUsage().inputTokenCount() : null;
        Integer outputTokens = chatResponse != null && chatResponse.tokenUsage() != null
                ? chatResponse.tokenUsage().outputTokenCount() : null;
        String model = chatResponse != null && chatResponse.modelName() != null ? chatResponse.modelName() : "none";
        timer(agent, "success").record(millis, TimeUnit.MILLISECONDS);
        WorkflowTrace trace = trace(response.agenticScope());
        if (trace != null) {
            trace.recordStep(agent.name(), kind(agent), Outcome.SUCCEEDED, millis, inputTokens, outputTokens,
                    describe(response.output()));
        }
        log.info("agent={} kind={} outcome=success durationMs={} model={} inputTokens={} outputTokens={} assetId={} workflowRunId={} correlationId={}",
                agent.name(), kind(agent), millis, model, inputTokens, outputTokens,
                id(response.agenticScope(), ASSET_KEY, CorrelationContext.ASSET_ID),
                id(response.agenticScope(), WORKFLOW_RUN_KEY, CorrelationContext.WORKFLOW_RUN_ID),
                id(response.agenticScope(), CORRELATION_KEY, CorrelationContext.CORRELATION_ID));
    }

    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
        AgentInstance agent = error.agent();
        if (!isStep(agent)) {
            return;
        }
        long millis = elapsedMillis(error.agenticScope(), agent);
        timer(agent, "failure").record(millis, TimeUnit.MILLISECONDS);
        String reason = rootCause(error.error());
        WorkflowTrace trace = trace(error.agenticScope());
        if (trace != null) {
            trace.recordStep(agent.name(), kind(agent), Outcome.FAILED, millis, null, null, reason);
        }
        log.warn("agent={} kind={} outcome=failure durationMs={} error={} assetId={} workflowRunId={} correlationId={}",
                agent.name(), kind(agent), millis, reason,
                id(error.agenticScope(), ASSET_KEY, CorrelationContext.ASSET_ID),
                id(error.agenticScope(), WORKFLOW_RUN_KEY, CorrelationContext.WORKFLOW_RUN_ID),
                id(error.agenticScope(), CORRELATION_KEY, CorrelationContext.CORRELATION_ID));
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution execution) {
        String agent = execution.agentInstance().name();
        String tool = execution.toolExecution().request().name();
        boolean failed = execution.toolExecution().hasFailed();
        registry.counter("dam.ai.tool.calls", "agent", agent, "tool", tool, "outcome", failed ? "failure" : "success")
                .increment();
        WorkflowTrace trace = trace(execution.agenticScope());
        if (trace != null) {
            trace.recordToolCall(agent, tool, failed);
        }
        log.info("agent={} tool={} outcome={} durationMs={}", agent, tool, failed ? "failure" : "success",
                execution.toolExecution().duration().toMillis());
    }

    private Timer timer(AgentInstance agent, String outcome) {
        return Timer.builder("dam.ai.agent.invocations")
                .description("Agent and workflow-step executions")
                .tag("agent", agent.name())
                .tag("kind", kind(agent).name())
                .tag("outcome", outcome)
                .register(registry);
    }

    private long elapsedMillis(AgenticScope scope, AgentInstance agent) {
        Long start = startTimes.remove(key(scope, agent));
        return start == null ? 0 : (System.nanoTime() - start) / 1_000_000;
    }

    private static boolean isStep(AgentInstance agent) {
        AgenticSystemTopology topology = agent.topology();
        return topology == AgenticSystemTopology.AI_AGENT || topology == AgenticSystemTopology.NON_AI_AGENT;
    }

    private static StepKind kind(AgentInstance agent) {
        return agent.topology() == AgenticSystemTopology.AI_AGENT ? StepKind.AI_AGENT : StepKind.DETERMINISTIC_STEP;
    }

    private static String key(AgenticScope scope, AgentInstance agent) {
        return System.identityHashCode(scope) + ":" + agent.agentId();
    }

    private static WorkflowTrace trace(AgenticScope scope) {
        return scope == null ? null : scope.readState(TRACE_KEY) instanceof WorkflowTrace trace ? trace : null;
    }

    private static String describe(Object output) {
        String detail = output instanceof StepOutcome outcome ? outcome.outcomeSummary()
                : output instanceof String text && !text.startsWith("{") && !text.startsWith("[") ? text : null;
        return detail == null || detail.length() <= 300 ? detail : detail.substring(0, 300) + "...";
    }

    /** Ids are read from the scope first because parallel/conditional branches may run on threads without MDC. */
    private static String id(AgenticScope scope, String scopeKey, String mdcKey) {
        Object value = scope == null ? null : scope.readState(scopeKey);
        return value != null ? value.toString() : MDC.get(mdcKey);
    }

    private static String rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null ? "" : ": " + cause.getMessage();
        return cause.getClass().getSimpleName() + (message.length() > 200 ? message.substring(0, 200) : message);
    }
}

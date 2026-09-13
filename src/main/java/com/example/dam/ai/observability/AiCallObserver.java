package com.example.dam.ai.observability;

import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.common.observability.CorrelationContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Observes standalone AI-service calls (outside the agentic workflow) with the same meter
 * ({@code dam.ai.agent.invocations}) and log format as {@link AgentObservabilityListener}.
 */
@Component
@ConditionalOnAiEnabled
public class AiCallObserver {

    private static final Logger log = LoggerFactory.getLogger(AiCallObserver.class);

    private final MeterRegistry registry;

    public AiCallObserver(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T observe(String agent, Supplier<T> call) {
        long start = System.nanoTime();
        String outcome = "failure";
        try {
            T result = call.get();
            outcome = "success";
            return result;
        } finally {
            long nanos = System.nanoTime() - start;
            Timer.builder("dam.ai.agent.invocations")
                    .tag("agent", agent)
                    .tag("kind", "AI_AGENT")
                    .tag("outcome", outcome)
                    .register(registry)
                    .record(nanos, TimeUnit.NANOSECONDS);
            log.info("agent={} kind=AI_AGENT outcome={} durationMs={} correlationId={}", agent, outcome,
                    nanos / 1_000_000, CorrelationContext.correlationId().orElse(null));
        }
    }
}

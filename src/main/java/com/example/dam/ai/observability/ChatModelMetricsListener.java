package com.example.dam.ai.observability;

import com.example.dam.ai.config.ConditionalOnAiEnabled;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records latency, outcome and token usage of every model call. Prompts and responses are never logged here.
 * Meters: {@code dam.ai.model.calls} (timer) and {@code dam.ai.model.tokens} (distribution summary).
 */
@Component
@ConditionalOnAiEnabled
public class ChatModelMetricsListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(ChatModelMetricsListener.class);
    private static final String START = "dam.startNanos";

    private final MeterRegistry registry;

    public ChatModelMetricsListener(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        context.attributes().put(START, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        ChatResponse response = context.chatResponse();
        String model = response.modelName() != null ? response.modelName() : String.valueOf(context.chatRequest().modelName());
        long nanos = elapsed(context.attributes().get(START));
        timer(context.modelProvider().name(), model, "success").record(nanos, TimeUnit.NANOSECONDS);
        TokenUsage usage = response.tokenUsage();
        if (usage != null) {
            record(model, "input", usage.inputTokenCount());
            record(model, "output", usage.outputTokenCount());
        }
        log.debug("Model call model={} durationMs={} tokens={}", model, nanos / 1_000_000, usage);
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        String model = String.valueOf(context.chatRequest().modelName());
        timer(context.modelProvider().name(), model, "failure")
                .record(elapsed(context.attributes().get(START)), TimeUnit.NANOSECONDS);
        log.warn("Model call failed model={} error={}", model, context.error().getClass().getSimpleName());
    }

    private Timer timer(String provider, String model, String outcome) {
        return Timer.builder("dam.ai.model.calls")
                .description("LLM calls")
                .tag("provider", provider)
                .tag("model", model)
                .tag("outcome", outcome)
                .register(registry);
    }

    private void record(String model, String type, Integer tokens) {
        if (tokens != null) {
            DistributionSummary.builder("dam.ai.model.tokens")
                    .baseUnit("tokens")
                    .tag("model", model)
                    .tag("type", type)
                    .register(registry)
                    .record(tokens);
        }
    }

    private static long elapsed(Object start) {
        return start instanceof Long startNanos ? System.nanoTime() - startNanos : 0L;
    }
}

package com.example.dam.ai.observability;

import com.example.dam.ai.config.AiModelInfo;
import com.example.dam.ai.config.ConditionalOnAiEnabled;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports the AI configuration without calling the model. A misconfigured provider yields UNKNOWN rather than
 * DOWN: the DAM keeps serving traffic because AI is an optional capability.
 */
@Component("ai")
@ConditionalOnAiEnabled
class AiHealthIndicator implements HealthIndicator {

    private final AiModelInfo modelInfo;

    AiHealthIndicator(AiModelInfo modelInfo) {
        this.modelInfo = modelInfo;
    }

    @Override
    public Health health() {
        Health.Builder builder = modelInfo.available() ? Health.up() : Health.unknown();
        builder.withDetail("provider", modelInfo.provider())
                .withDetail("chatModel", modelInfo.chatModel())
                .withDetail("embeddingModel", modelInfo.embeddingModel());
        if (!modelInfo.available()) {
            builder.withDetail("reason", modelInfo.unavailableReason());
        }
        return builder.build();
    }
}

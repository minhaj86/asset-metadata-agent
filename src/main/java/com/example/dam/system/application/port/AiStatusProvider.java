package com.example.dam.system.application.port;

import java.time.Duration;

/** Port implemented by the AI layer: configuration and availability of the AI capability (no model call). */
public interface AiStatusProvider {

    AiStatus aiStatus();

    /**
     * @param available         {@code false} when the provider is misconfigured; AI calls then fail fast
     * @param unavailableReason why the AI capability cannot be used, if it cannot
     */
    record AiStatus(boolean available, String provider, String chatModel, String embeddingModel,
                    String unavailableReason, Duration workflowTimeout, int maxToolInvocations) {
    }
}

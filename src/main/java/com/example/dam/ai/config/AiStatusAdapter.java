package com.example.dam.ai.config;

import com.example.dam.system.application.port.AiStatusProvider;
import org.springframework.stereotype.Component;

/** Publishes the AI configuration and availability to the system status (no model call). */
@Component
@ConditionalOnAiEnabled
class AiStatusAdapter implements AiStatusProvider {

    private final AiModelInfo modelInfo;
    private final AiProperties properties;

    AiStatusAdapter(AiModelInfo modelInfo, AiProperties properties) {
        this.modelInfo = modelInfo;
        this.properties = properties;
    }

    @Override
    public AiStatus aiStatus() {
        return new AiStatus(modelInfo.available(), modelInfo.provider(), modelInfo.chatModel(),
                modelInfo.embeddingModel(), modelInfo.unavailableReason(), properties.workflow().timeout(),
                properties.workflow().maxToolInvocations());
    }
}

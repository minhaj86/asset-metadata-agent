package com.example.dam.ai.config;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import com.example.dam.ai.model.demo.OfflineDemoChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the LangChain4j {@link ChatModel} for the configured provider. Nothing else in the application knows
 * which vendor is used; switching provider is a configuration change ({@code AI_PROVIDER}).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAiEnabled
class ChatModelConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChatModelConfiguration.class);

    @Bean
    AiModelInfo aiModelInfo(AiProperties properties) {
        AiProperties.Embedding embedding = properties.embedding();
        String embeddingModel = switch (embedding.provider()) {
            case LOCAL -> "all-minilm-l6-v2";
            case OPENAI -> orDefault(embedding.model(), "text-embedding-3-small");
            case OLLAMA -> orDefault(embedding.model(), "nomic-embed-text");
        };
        String missingKey = switch (properties.provider()) {
            case OPENAI -> isBlank(properties.openai().apiKey()) ? "OPENAI_API_KEY is not set" : null;
            case ANTHROPIC -> isBlank(properties.anthropic().apiKey()) ? "ANTHROPIC_API_KEY is not set" : null;
            case DEMO, OLLAMA -> null;
        };
        String chatModel = switch (properties.provider()) {
            case DEMO -> OfflineDemoChatModel.MODEL_NAME;
            case OPENAI -> properties.openai().model();
            case ANTHROPIC -> properties.anthropic().model();
            case OLLAMA -> properties.ollama().model();
        };
        return new AiModelInfo(properties.provider().name().toLowerCase(), chatModel, embeddingModel,
                missingKey == null, missingKey);
    }

    @Bean
    ChatModel chatModel(AiProperties properties, AiModelInfo modelInfo, List<ChatModelListener> listeners) {
        if (!modelInfo.available()) {
            log.error("AI provider '{}' is unavailable: {}. AI features will answer 503; core DAM features are unaffected.",
                    modelInfo.provider(), modelInfo.unavailableReason());
            return new UnavailableChatModel("AI provider %s unavailable: %s".formatted(modelInfo.provider(),
                    modelInfo.unavailableReason()), listeners);
        }
        AiProperties.Chat chat = properties.chat();
        Set<Capability> capabilities = chat.jsonSchema() ? Set.of(RESPONSE_FORMAT_JSON_SCHEMA) : Set.of();
        ChatModel model = switch (properties.provider()) {
            case DEMO -> new OfflineDemoChatModel(listeners, properties.demo().simulatedLatency());
            case OPENAI -> {
                OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                        .apiKey(properties.openai().apiKey())
                        .modelName(properties.openai().model())
                        .temperature(chat.temperature())
                        .timeout(chat.timeout())
                        .maxRetries(chat.maxRetries())
                        .logRequests(chat.logRequests())
                        .logResponses(chat.logResponses())
                        .listeners(listeners);
                if (!isBlank(properties.openai().baseUrl())) {
                    builder.baseUrl(properties.openai().baseUrl());
                }
                if (chat.jsonSchema()) {
                    builder.responseFormat("json_schema").strictJsonSchema(true);
                }
                yield builder.build();
            }
            case ANTHROPIC -> AnthropicChatModel.builder()
                    .apiKey(properties.anthropic().apiKey())
                    .modelName(properties.anthropic().model())
                    .temperature(chat.temperature())
                    .maxTokens(4096)
                    .timeout(chat.timeout())
                    .maxRetries(chat.maxRetries())
                    .supportedCapabilities(capabilities)
                    .logRequests(chat.logRequests())
                    .logResponses(chat.logResponses())
                    .listeners(listeners)
                    .build();
            case OLLAMA -> OllamaChatModel.builder()
                    .baseUrl(properties.ollama().baseUrl())
                    .modelName(properties.ollama().model())
                    .temperature(chat.temperature())
                    .timeout(chat.timeout())
                    .maxRetries(chat.maxRetries())
                    .supportedCapabilities(capabilities)
                    .logRequests(chat.logRequests())
                    .logResponses(chat.logResponses())
                    .listeners(listeners)
                    .build();
        };
        log.info("AI chat model: provider={}, model={}", modelInfo.provider(), modelInfo.chatModel());
        return model;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String orDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }
}

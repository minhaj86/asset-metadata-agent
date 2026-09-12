package com.example.dam.ai.model.demo;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Deterministic, rule-based stand-in for an LLM, selected with {@code ai.provider=demo}.
 * <p>
 * It implements the LangChain4j {@link ChatModel} protocol faithfully - JSON-schema structured outputs and
 * multi-step tool calling - so the complete agentic workflow (agents, shared scope, tools, RAG, conditional
 * routing, listeners, metrics) runs offline and reproducibly. It is <b>not</b> a language model: its "reasoning"
 * consists of transparent heuristics over the structured context the prompts provide, dispatched by the requested
 * response schema. Use a real provider (openai, anthropic, ollama) for genuine model behaviour.
 */
public final class OfflineDemoChatModel implements ChatModel {

    public static final String MODEL_NAME = "offline-demo-rules-v1";

    private final List<ChatModelListener> listeners;
    private final Map<String, DemoResponder> responders;
    private final Duration simulatedLatency;

    public OfflineDemoChatModel(List<ChatModelListener> listeners) {
        this(listeners, Duration.ZERO);
    }

    /**
     * @param simulatedLatency delay per call imitating model latency (makes live workflow progress observable)
     */
    public OfflineDemoChatModel(List<ChatModelListener> listeners, Duration simulatedLatency) {
        this.listeners = List.copyOf(listeners);
        this.simulatedLatency = simulatedLatency == null ? Duration.ZERO : simulatedLatency;
        DemoSupport support = new DemoSupport();
        this.responders = Stream.of(
                        new ExtractionResponder(support),
                        new EnrichmentResponder(support),
                        new TaxonomyResponder(support),
                        new QualityReviewResponder(support),
                        new SearchIntentResponder(support),
                        new KnowledgeAnswerResponder(support))
                .collect(Collectors.toUnmodifiableMap(DemoResponder::schemaName, Function.identity()));
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        String schema = Optional.ofNullable(request.responseFormat())
                .map(ResponseFormat::jsonSchema)
                .map(JsonSchema::name)
                .orElseThrow(() -> new IllegalStateException("The offline demo model only supports structured outputs"));
        DemoResponder responder = responders.get(schema);
        if (responder == null) {
            throw new IllegalStateException("The offline demo model has no rules for response type " + schema);
        }
        simulateLatency();
        DemoReply reply = responder.reply(new DemoConversation(request));
        AiMessage message = reply.isToolCall()
                ? AiMessage.from(ToolExecutionRequest.builder()
                .id(UUID.randomUUID().toString())
                .name(reply.toolName())
                .arguments(reply.toolArguments())
                .build())
                : AiMessage.from(reply.text());
        return ChatResponse.builder()
                .aiMessage(message)
                .modelName(MODEL_NAME)
                .tokenUsage(new TokenUsage(estimateTokens(request.messages()), estimateTokens(reply)))
                .finishReason(reply.isToolCall() ? FinishReason.TOOL_EXECUTION : FinishReason.STOP)
                .build();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return DefaultChatRequestParameters.builder().modelName(MODEL_NAME).build();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OTHER;
    }

    /** Rough estimate (4 characters per token) so that token metrics are meaningful in demos. */
    private static int estimateTokens(List<ChatMessage> messages) {
        int characters = 0;
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage system) {
                characters += system.text().length();
            } else if (message instanceof UserMessage user && user.hasSingleText()) {
                characters += user.singleText().length();
            } else if (message instanceof ToolExecutionResultMessage tool) {
                characters += tool.text().length();
            } else if (message instanceof AiMessage ai && ai.text() != null) {
                characters += ai.text().length();
            }
        }
        return Math.max(1, characters / 4);
    }

    private static int estimateTokens(DemoReply reply) {
        String text = reply.isToolCall() ? reply.toolArguments() : reply.text();
        return Math.max(1, text.length() / 4);
    }

    private void simulateLatency() {
        if (simulatedLatency.isZero() || simulatedLatency.isNegative()) {
            return;
        }
        try {
            Thread.sleep(simulatedLatency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Demo model call interrupted", e);
        }
    }
}

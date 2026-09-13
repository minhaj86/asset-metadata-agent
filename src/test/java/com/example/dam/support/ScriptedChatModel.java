package com.example.dam.support;

import com.example.dam.ai.model.demo.OfflineDemoChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deterministic test double for the LLM. Per structured-output type a test can script the answer or simulate an
 * outage; everything else is answered by the offline demo model. No paid model is ever called by tests.
 */
public class ScriptedChatModel implements ChatModel {

    private final OfflineDemoChatModel demo = new OfflineDemoChatModel(List.of());
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, String> scripted = new ConcurrentHashMap<>();
    private final Map<String, RuntimeException> failures = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();

    /** Answers every request for {@code outputType} with the given (serialised) object. */
    public void respond(Class<?> outputType, Object output) {
        scripted.put(outputType.getSimpleName(), json.writeValueAsString(output));
    }

    /** Answers with raw text, e.g. malformed JSON. */
    public void respondRaw(Class<?> outputType, String text) {
        scripted.put(outputType.getSimpleName(), text);
    }

    /** Simulates an unavailable model for requests expecting {@code outputType}. */
    public void fail(Class<?> outputType) {
        failures.put(outputType.getSimpleName(), new IllegalStateException("Simulated LLM outage"));
    }

    public long requestsFor(Class<?> outputType) {
        return requests.stream().filter(outputType.getSimpleName()::equals).count();
    }

    public void reset() {
        scripted.clear();
        failures.clear();
        requests.clear();
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        String schema = request.responseFormat().jsonSchema().name();
        requests.add(schema);
        RuntimeException failure = failures.get(schema);
        if (failure != null) {
            throw failure;
        }
        String answer = scripted.get(schema);
        if (answer == null) {
            return demo.doChat(request);
        }
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(answer))
                .modelName("scripted-test-model")
                .tokenUsage(new TokenUsage(10, 10))
                .finishReason(FinishReason.STOP)
                .build();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }
}

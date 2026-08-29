package com.example.dam.ai.config;

import com.example.dam.common.domain.CapabilityUnavailableException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;

/**
 * Stand-in used when the configured provider cannot be initialised (e.g. missing API key). Every call fails
 * fast with a clear message so that AI endpoints answer 503 while the rest of the application keeps working.
 */
final class UnavailableChatModel implements ChatModel {

    private final String reason;
    private final List<ChatModelListener> listeners;

    UnavailableChatModel(String reason, List<ChatModelListener> listeners) {
        this.reason = reason;
        this.listeners = List.copyOf(listeners);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        throw new CapabilityUnavailableException("AI_PROVIDER_UNAVAILABLE", reason);
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }
}

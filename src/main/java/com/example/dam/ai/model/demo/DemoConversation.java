package com.example.dam.ai.model.demo;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Read access to the request the demo model received: tagged prompt sections, available tools, tool results. */
final class DemoConversation {

    private final ChatRequest request;

    DemoConversation(ChatRequest request) {
        this.request = request;
    }

    String userText() {
        List<ChatMessage> messages = request.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage user) {
                return user.contents().stream()
                        .filter(TextContent.class::isInstance)
                        .map(content -> ((TextContent) content).text())
                        .collect(Collectors.joining("\n"));
            }
        }
        return "";
    }

    /** Content between {@code <tag>} and {@code </tag>} in the user message. */
    Optional<String> section(String tag) {
        Matcher matcher = Pattern.compile("<" + tag + ">\\s*(.*?)\\s*</" + tag + ">", Pattern.DOTALL)
                .matcher(userText());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    boolean toolAvailable(String toolName) {
        return request.toolSpecifications() != null
                && request.toolSpecifications().stream().anyMatch(tool -> tool.name().equals(toolName));
    }

    Optional<String> lastToolResult(String toolName) {
        String result = null;
        for (ChatMessage message : request.messages()) {
            if (message instanceof ToolExecutionResultMessage toolResult && toolName.equals(toolResult.toolName())) {
                result = toolResult.text();
            }
        }
        return Optional.ofNullable(result);
    }
}

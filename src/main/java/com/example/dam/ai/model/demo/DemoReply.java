package com.example.dam.ai.model.demo;

/** Either a final (JSON) answer or a tool call. */
record DemoReply(String text, String toolName, String toolArguments) {

    static DemoReply answer(String json) {
        return new DemoReply(json, null, null);
    }

    static DemoReply callTool(String toolName, String argumentsJson) {
        return new DemoReply(null, toolName, argumentsJson);
    }

    boolean isToolCall() {
        return toolName != null;
    }
}

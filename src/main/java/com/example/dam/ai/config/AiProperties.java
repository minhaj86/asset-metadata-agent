package com.example.dam.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuration of the AI layer ({@code ai.*}). API keys come from environment variables only. */
@ConfigurationProperties("ai")
public record AiProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("demo") Provider provider,
        @DefaultValue Chat chat,
        @DefaultValue OpenAi openai,
        @DefaultValue Anthropic anthropic,
        @DefaultValue Ollama ollama,
        @DefaultValue Embedding embedding,
        @DefaultValue Workflow workflow,
        @DefaultValue Knowledge knowledge,
        @DefaultValue Demo demo) {

    public enum Provider { DEMO, OPENAI, ANTHROPIC, OLLAMA }

    /**
     * Offline demo model settings.
     *
     * @param simulatedLatency delay per model call so that live workflow progress is observable in demos; real
     *                         providers have real latency, tests use zero
     */
    public record Demo(@DefaultValue("0ms") Duration simulatedLatency) {
    }

    public enum EmbeddingProvider { LOCAL, OPENAI, OLLAMA }

    public record Chat(@DefaultValue("0.0") double temperature,
                       @DefaultValue("60s") Duration timeout,
                       @DefaultValue("2") int maxRetries,
                       @DefaultValue("true") boolean jsonSchema,
                       @DefaultValue("false") boolean logRequests,
                       @DefaultValue("false") boolean logResponses) {
    }

    public record OpenAi(String apiKey, @DefaultValue("gpt-4.1-mini") String model, String baseUrl) {
    }

    public record Anthropic(String apiKey, @DefaultValue("claude-sonnet-4-5") String model) {
    }

    public record Ollama(@DefaultValue("http://localhost:11434") String baseUrl,
                         @DefaultValue("llama3.1:8b") String model) {
    }

    public record Embedding(@DefaultValue("local") EmbeddingProvider provider,
                            @DefaultValue("384") int dimension,
                            String model) {
    }

    public record Workflow(@DefaultValue("180s") Duration timeout,
                           @DefaultValue("5") int maxToolInvocations) {
    }

    public record Knowledge(@DefaultValue("true") boolean ingestOnStartup,
                            @DefaultValue("classpath*:knowledge/*.md") String location,
                            @DefaultValue("4") int maxResults,
                            @DefaultValue("0.60") double minScore) {
    }
}

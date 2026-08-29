package com.example.dam.ai.config;

/**
 * Identifies the models in use; recorded as provenance ({@code aiModel}) on AI-generated metadata.
 *
 * @param available {@code false} when the provider is misconfigured; AI calls then fail fast with 503
 */
public record AiModelInfo(String provider, String chatModel, String embeddingModel, boolean available,
                          String unavailableReason) {

    /** Compact provenance label, e.g. {@code openai:gpt-4.1-mini}. */
    public String chatModelLabel() {
        return provider + ":" + chatModel;
    }
}

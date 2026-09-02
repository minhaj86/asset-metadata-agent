package com.example.dam.ai.agent;

import com.example.dam.ai.config.ConditionalOnAiEnabled;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Loads agent prompts from {@code classpath:prompts/}. Every system prompt is prefixed with the shared
 * governance rules ({@code _shared-rules.md}) so the critical instructions exist exactly once.
 */
@Component
@ConditionalOnAiEnabled
public class PromptCatalog {

    private static final String LOCATION = "classpath:prompts/";

    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public PromptCatalog(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String system(String agent) {
        return cache.computeIfAbsent(agent + ".system", key -> load(agent + ".system.md") + "\n\n" + load("_shared-rules.md"));
    }

    public String user(String agent) {
        return cache.computeIfAbsent(agent + ".user", key -> load(agent + ".user.md"));
    }

    private String load(String file) {
        try {
            return resourceLoader.getResource(LOCATION + file).getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Missing prompt resource " + file, e);
        }
    }
}

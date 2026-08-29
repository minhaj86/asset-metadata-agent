package com.example.dam.ai.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/**
 * Marks AI-layer beans. With {@code ai.enabled=false} none of them exist and the application runs as a purely
 * deterministic DAM metadata service (AI endpoints answer 503, search degrades to structured search).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnBooleanProperty(name = "ai.enabled", matchIfMissing = true)
public @interface ConditionalOnAiEnabled {
}

package com.example.dam.metadata.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Confidence thresholds for AI proposals, see {@code ai.metadata.confidence.*}. */
@ConfigurationProperties("ai.metadata.confidence")
public record ConfidenceThresholdProperties(
        @DefaultValue("0.92") double autoApprove,
        @DefaultValue("0.70") double humanReview) {
}

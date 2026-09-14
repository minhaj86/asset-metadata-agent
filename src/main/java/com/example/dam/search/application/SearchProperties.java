package com.example.dam.search.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("dam.search")
public record SearchProperties(
        @DefaultValue("20") int defaultLimit,
        @DefaultValue("100") int maxLimit,
        @DefaultValue("0.20") double semanticMinSimilarity,
        @DefaultValue("0.95") double duplicateSimilarity,
        @DefaultValue("0.80") double similarSimilarity,
        @DefaultValue("0.70") double recommendationSimilarity) {
}

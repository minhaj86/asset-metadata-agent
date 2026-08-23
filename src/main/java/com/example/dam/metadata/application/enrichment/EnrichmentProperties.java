package com.example.dam.metadata.application.enrichment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Execution limits for enrichment runs, see {@code dam.enrichment.*}.
 *
 * @param maxConcurrentRuns workflows executing at the same time; further runs wait in status QUEUED
 * @param maxBulkAssets     assets per bulk enrichment request
 */
@ConfigurationProperties("dam.enrichment")
public record EnrichmentProperties(@DefaultValue("2") int maxConcurrentRuns, @DefaultValue("25") int maxBulkAssets) {

    public EnrichmentProperties {
        if (maxConcurrentRuns < 1 || maxBulkAssets < 1) {
            throw new IllegalArgumentException("dam.enrichment limits must be positive");
        }
    }
}

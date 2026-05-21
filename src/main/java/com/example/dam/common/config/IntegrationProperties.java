package com.example.dam.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Locations of the mock integration catalogs used by the POC adapters, and DAM write-back.
 *
 * @param mockDamBaseUrl public base URL of the mock DAM's rendition endpoint (asset URLs of mock assets)
 */
@ConfigurationProperties("dam.integration")
public record IntegrationProperties(
        @DefaultValue("classpath:sample-data/mock-dam-catalog.yaml") String damCatalog,
        @DefaultValue("classpath:sample-data/mock-products.yaml") String productCatalog,
        @DefaultValue("true") boolean writeBack,
        @DefaultValue("http://localhost:8080/mock-dam") String mockDamBaseUrl) {
}

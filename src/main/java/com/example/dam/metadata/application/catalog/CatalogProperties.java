package com.example.dam.metadata.application.catalog;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Presentation settings of the asset catalog, see {@code dam.catalog.*}.
 *
 * @param keyFields metadata fields returned with every catalog row (compact display, sortable)
 */
@ConfigurationProperties("dam.catalog")
public record CatalogProperties(
        @DefaultValue({"productId", "productType", "category", "color", "title", "taxonomyPath", "campaign"})
        List<String> keyFields) {

    public CatalogProperties {
        keyFields = List.copyOf(keyFields);
    }
}

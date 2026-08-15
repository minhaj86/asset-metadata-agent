package com.example.dam.metadata.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("dam.metadata")
public record MetadataProperties(
        @DefaultValue("classpath:metadata-schema/dam-metadata-schema.yaml") String schemaLocation,
        @DefaultValue("true") boolean syncSchemaOnStartup) {
}

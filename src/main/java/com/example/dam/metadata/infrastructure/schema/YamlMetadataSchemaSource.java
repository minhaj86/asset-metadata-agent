package com.example.dam.metadata.infrastructure.schema;

import com.example.dam.metadata.application.MetadataProperties;
import com.example.dam.metadata.application.port.MetadataSchemaSource;
import com.example.dam.metadata.domain.schema.MetadataSchemaDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
class YamlMetadataSchemaSource implements MetadataSchemaSource {

    private final ResourceLoader resourceLoader;
    private final MetadataProperties properties;
    private final YamlMetadataSchemaParser parser = new YamlMetadataSchemaParser();

    YamlMetadataSchemaSource(ResourceLoader resourceLoader, MetadataProperties properties) {
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    @Override
    public MetadataSchemaDefinition load() {
        try (InputStream in = resourceLoader.getResource(properties.schemaLocation()).getInputStream()) {
            return parser.parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read metadata schema from " + properties.schemaLocation(), e);
        }
    }

    @Override
    public String location() {
        return properties.schemaLocation();
    }
}

package com.example.dam.metadata.application.port;

import com.example.dam.metadata.domain.schema.MetadataSchemaDefinition;

/** Source of the configured metadata schema (YAML in this POC; could be a schema registry). */
public interface MetadataSchemaSource {

    MetadataSchemaDefinition load();

    String location();
}

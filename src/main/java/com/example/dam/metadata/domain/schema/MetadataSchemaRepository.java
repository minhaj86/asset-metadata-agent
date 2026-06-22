package com.example.dam.metadata.domain.schema;

import java.util.Optional;

/** Persistence port for the active metadata schema. */
public interface MetadataSchemaRepository {

    Optional<MetadataSchemaDefinition> load();

    /** Atomically replaces the stored schema with the given definition. */
    void replace(MetadataSchemaDefinition definition);
}

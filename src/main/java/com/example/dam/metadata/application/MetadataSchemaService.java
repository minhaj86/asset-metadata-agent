package com.example.dam.metadata.application;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.schema.MetadataSchemaDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchemaRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Read-mostly access to the active metadata schema with an in-memory cache. */
@Service
public class MetadataSchemaService {

    private final MetadataSchemaRepository repository;
    private volatile MetadataSchemaDefinition cached;

    public MetadataSchemaService(MetadataSchemaRepository repository) {
        this.repository = repository;
    }

    public MetadataSchemaDefinition definition() {
        MetadataSchemaDefinition current = cached;
        if (current == null) {
            current = repository.load()
                    .orElseThrow(() -> new IllegalStateException("Metadata schema has not been initialised"));
            cached = current;
        }
        return current;
    }

    public MetadataSchema schemaFor(AssetType assetType) {
        return definition().forAssetType(assetType);
    }

    public Optional<MetadataFieldDefinition> field(String key) {
        return definition().field(key);
    }

    /** Stores a new schema version and refreshes the cache. */
    public void replace(MetadataSchemaDefinition definition) {
        repository.replace(definition);
        cached = definition;
    }

    public Optional<MetadataSchemaDefinition> stored() {
        return repository.load();
    }
}

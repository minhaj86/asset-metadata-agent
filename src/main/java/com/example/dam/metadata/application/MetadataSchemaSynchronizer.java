package com.example.dam.metadata.application;

import com.example.dam.metadata.application.port.MetadataSchemaSource;
import com.example.dam.metadata.domain.schema.MetadataSchemaDefinition;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Synchronises the configured schema into the database before the application accepts traffic
 * ({@link SmartInitializingSingleton} runs before the web server starts).
 */
@Component
class MetadataSchemaSynchronizer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MetadataSchemaSynchronizer.class);

    private final MetadataSchemaSource source;
    private final MetadataSchemaService schemaService;
    private final MetadataProperties properties;

    MetadataSchemaSynchronizer(MetadataSchemaSource source, MetadataSchemaService schemaService,
                               MetadataProperties properties) {
        this.source = source;
        this.schemaService = schemaService;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Optional<MetadataSchemaDefinition> stored = schemaService.stored();
        if (!properties.syncSchemaOnStartup() && stored.isPresent()) {
            log.info("Metadata schema sync disabled; using stored schema v{}", stored.get().version());
            return;
        }
        MetadataSchemaDefinition configured = source.load();
        if (stored.isPresent() && stored.get().version() > configured.version()) {
            log.warn("Stored metadata schema v{} is newer than configured v{} ({}); keeping stored schema",
                    stored.get().version(), configured.version(), source.location());
            return;
        }
        if (stored.isPresent() && stored.get().equals(configured)) {
            log.info("Metadata schema v{} is up to date ({} fields)", configured.version(), configured.fields().size());
            return;
        }
        schemaService.replace(configured);
        log.info("Metadata schema v{} synchronised from {} ({} fields, {} rules)", configured.version(),
                source.location(), configured.fields().size(), configured.rules().size());
    }
}

package com.example.dam.metadata.application.catalog;

import com.example.dam.asset.domain.AssetNotFoundException;
import com.example.dam.common.domain.InvalidRequestException;
import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.application.catalog.AssetCatalogRepository.Context;
import com.example.dam.metadata.domain.schema.CrossFieldRule;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchemaDefinition;
import com.example.dam.metadata.domain.schema.WellKnownFields;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asset catalog: paged, filtered and sorted listing of assets with their derived metadata summary. Filters are
 * validated against the configurable schema; the listing itself is a deterministic database query.
 */
@Service
public class AssetCatalogService {

    private static final int DEFAULT_RIGHTS_WARNING_DAYS = 30;

    private final AssetCatalogRepository repository;
    private final MetadataSchemaService schemaService;
    private final CatalogProperties properties;
    private final Clock clock;

    public AssetCatalogService(AssetCatalogRepository repository, MetadataSchemaService schemaService,
                               CatalogProperties properties, Clock clock) {
        this.repository = repository;
        this.schemaService = schemaService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CatalogItem.Page search(CatalogQuery query) {
        Context context = context();
        validate(query, context);
        return repository.search(query, context);
    }

    @Transactional(readOnly = true)
    public CatalogItem get(UUID assetId) {
        return repository.findById(assetId, context()).orElseThrow(() -> new AssetNotFoundException(assetId));
    }

    /** Key metadata fields of catalog rows (configured fields that exist in the schema). */
    public List<String> keyFields() {
        MetadataSchemaDefinition definition = schemaService.definition();
        return properties.keyFields().stream().filter(key -> definition.field(key).isPresent()).toList();
    }

    private Context context() {
        MetadataSchemaDefinition definition = schemaService.definition();
        int warningDays = definition.rules().stream()
                .filter(CrossFieldRule.RightsExpiry.class::isInstance)
                .map(rule -> ((CrossFieldRule.RightsExpiry) rule).warningWindowDays())
                .findFirst()
                .orElse(DEFAULT_RIGHTS_WARNING_DAYS);
        LocalDate today = LocalDate.now(clock);
        return new Context(today, today.plusDays(warningDays), keyFields(), WellKnownFields.TAXONOMY_PATH);
    }

    private void validate(CatalogQuery query, Context context) {
        MetadataSchemaDefinition definition = schemaService.definition();
        for (CatalogQuery.FieldFilter filter : query.filters()) {
            MetadataFieldDefinition field = definition.field(filter.field()).orElseThrow(() ->
                    new InvalidRequestException("UNSUPPORTED_FILTER", "Unknown metadata field: " + filter.field()));
            if (!field.searchable()) {
                throw new InvalidRequestException("UNSUPPORTED_FILTER",
                        "Field '%s' is not searchable".formatted(filter.field()));
            }
        }
        if (query.missingField() != null && definition.field(query.missingField()).isEmpty()) {
            throw new InvalidRequestException("UNSUPPORTED_FILTER", "Unknown metadata field: " + query.missingField());
        }
        if (query.sort().column() == CatalogSort.Column.METADATA_FIELD
                && !context.keyFields().contains(query.sort().metadataField())) {
            throw new InvalidRequestException("UNSUPPORTED_SORT",
                    "Sorting is supported for key fields only: " + context.keyFields());
        }
    }
}

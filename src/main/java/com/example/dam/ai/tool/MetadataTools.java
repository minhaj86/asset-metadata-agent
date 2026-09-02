package com.example.dam.ai.tool;

import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.application.MetadataService;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Read-only metadata tools. */
@Component
@ConditionalOnAiEnabled
public class MetadataTools {

    private final MetadataService metadataService;
    private final MetadataSchemaService schemaService;

    public MetadataTools(MetadataService metadataService, MetadataSchemaService schemaService) {
        this.metadataService = metadataService;
        this.schemaService = schemaService;
    }

    public record FieldDefinitionView(String key, String displayName, String type, boolean required,
                                      boolean repeatable, List<String> allowedValues, boolean aiMayPropose,
                                      boolean humanApprovalRequired, String description) {
    }

    @Tool("Returns the current metadata of an asset: field -> {value, source, status}. Read-only.")
    public Map<String, Object> getMetadata(@P("Internal asset UUID") String assetId) {
        Map<String, Object> result = new LinkedHashMap<>();
        metadataService.getMetadata(UUID.fromString(assetId.strip())).metadata().entries()
                .forEach((key, entry) -> result.put(key, Map.of(
                        "value", entry.value().toPlainValue(),
                        "source", entry.source().name(),
                        "status", entry.status().name())));
        return result;
    }

    @Tool("Returns the metadata schema for an asset type: fields, data types, vocabularies and AI policy")
    public List<FieldDefinitionView> getMetadataSchema(@P("Asset type, e.g. PRODUCT_IMAGE") String assetType) {
        MetadataSchema schema = schemaService.schemaFor(AssetType.valueOf(assetType.strip().toUpperCase(Locale.ROOT)));
        return schema.fieldDefinitions().stream()
                .map(field -> new FieldDefinitionView(field.key(), field.displayName(), field.dataType().name(),
                        schema.isRequired(field.key()), field.repeatable(), field.allowedValues(),
                        field.aiPolicy().enrichable(), field.aiPolicy().humanApprovalRequired(), field.description()))
                .toList();
    }
}

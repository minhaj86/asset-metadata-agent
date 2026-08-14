package com.example.dam.metadata.infrastructure.persistence;

import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.domain.validation.MetadataValueParser;
import com.example.dam.metadata.domain.validation.ParseResult;
import com.example.dam.metadata.domain.value.MetadataValue;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts typed metadata values to and from their JSONB representation. Reading is schema-aware (dates,
 * vocabularies); values that no longer fit the schema are kept as-is so that validation can report them.
 */
@Component
public class MetadataValueJsonCodec {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;
    private final MetadataSchemaService schemaService;
    private final MetadataValueParser parser;

    public MetadataValueJsonCodec(JsonMapper jsonMapper, MetadataSchemaService schemaService,
                                  MetadataValueParser parser) {
        this.jsonMapper = jsonMapper;
        this.schemaService = schemaService;
        this.parser = parser;
    }

    public String toJson(MetadataValue value) {
        return value == null ? null : jsonMapper.writeValueAsString(value.toPlainValue());
    }

    public MetadataValue fromJson(String fieldKey, String json) {
        if (json == null) {
            return null;
        }
        Object plain = jsonMapper.readerFor(Object.class)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readValue(json);
        return schemaService.field(fieldKey)
                .map(definition -> parser.parse(definition, plain))
                .filter(ParseResult::isSuccess)
                .map(ParseResult::value)
                .orElseGet(() -> parser.parseUntyped(plain));
    }

    public String stringListToJson(List<String> values) {
        return jsonMapper.writeValueAsString(values == null ? List.of() : values);
    }

    public List<String> stringListFromJson(String json) {
        return json == null ? List.of() : jsonMapper.readValue(json, STRING_LIST);
    }
}

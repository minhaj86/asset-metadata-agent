package com.example.dam.metadata.infrastructure.schema;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.domain.schema.AiFieldPolicy;
import com.example.dam.metadata.domain.schema.CrossFieldRule;
import com.example.dam.metadata.domain.schema.FieldRequirement;
import com.example.dam.metadata.domain.schema.MetadataDataType;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataFieldGroup;
import com.example.dam.metadata.domain.schema.MetadataSchemaDefinition;
import com.example.dam.metadata.domain.schema.ValidationRules;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Parses the YAML schema format documented in {@code metadata-schema/dam-metadata-schema.yaml}. */
public final class YamlMetadataSchemaParser {

    public MetadataSchemaDefinition parse(InputStream yaml) {
        Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        if (root == null) {
            throw new IllegalArgumentException("Metadata schema document is empty");
        }
        int version = ((Number) root.getOrDefault("version", 1)).intValue();
        return new MetadataSchemaDefinition(version, fields(map(root.get("fields"))),
                requirements(map(root.get("assetTypes"))), rules(list(root.get("rules"))),
                groups(list(root.get("groups"))));
    }

    private List<MetadataFieldGroup> groups(List<Object> groups) {
        return groups.stream().map(this::map)
                .map(group -> new MetadataFieldGroup(string(group, "key"), (String) group.get("displayName")))
                .toList();
    }

    private List<MetadataFieldDefinition> fields(Map<String, Object> fields) {
        List<MetadataFieldDefinition> definitions = new ArrayList<>();
        fields.forEach((key, rawField) -> {
            Map<String, Object> field = map(rawField);
            Map<String, Object> validation = map(field.get("validation"));
            Map<String, Object> ai = map(field.get("ai"));
            boolean enrichable = bool(ai, "enrichable", false);
            definitions.add(new MetadataFieldDefinition(
                    key,
                    (String) field.get("displayName"),
                    MetadataDataType.valueOf(string(field, "type").toUpperCase(Locale.ROOT)),
                    bool(field, "required", false),
                    bool(field, "repeatable", false),
                    bool(field, "searchable", false),
                    bool(field, "semanticSearch", false),
                    list(field.get("allowedValues")).stream().map(String::valueOf).toList(),
                    new ValidationRules(
                            (String) validation.get("pattern"),
                            integer(validation, "minLength"),
                            integer(validation, "maxLength"),
                            decimal(validation, "minValue"),
                            decimal(validation, "maxValue"),
                            integer(validation, "maxItems")),
                    new AiFieldPolicy(enrichable, bool(ai, "autoApply", false),
                            bool(ai, "humanApprovalRequired", !enrichable)),
                    (String) field.get("description"),
                    (String) field.get("group")));
        });
        return definitions;
    }

    private Map<AssetType, Map<String, FieldRequirement>> requirements(Map<String, Object> assetTypes) {
        Map<AssetType, Map<String, FieldRequirement>> requirements = new EnumMap<>(AssetType.class);
        assetTypes.forEach((type, rawRequirements) -> {
            Map<String, Object> perType = map(rawRequirements);
            Map<String, FieldRequirement> resolved = new LinkedHashMap<>();
            list(perType.get("recommended")).forEach(key -> resolved.put(String.valueOf(key), FieldRequirement.RECOMMENDED));
            list(perType.get("required")).forEach(key -> resolved.put(String.valueOf(key), FieldRequirement.REQUIRED));
            requirements.put(AssetType.valueOf(type), resolved);
        });
        return requirements;
    }

    private List<CrossFieldRule> rules(List<Object> rules) {
        return rules.stream().map(this::map).map(rule -> {
            String id = string(rule, "id");
            return switch (string(rule, "type")) {
                case "DATE_RANGE" -> (CrossFieldRule) new CrossFieldRule.DateRange(id, string(rule, "startField"),
                        string(rule, "endField"));
                case "RIGHTS_EXPIRY" -> new CrossFieldRule.RightsExpiry(id, string(rule, "field"),
                        integer(rule, "warningWindowDays") == null ? 0 : integer(rule, "warningWindowDays"));
                case "CONDITIONAL_REQUIRED" -> new CrossFieldRule.ConditionalRequired(id, string(rule, "whenField"),
                        string(rule, "whenValue"), string(rule, "requiredField"));
                default -> throw new IllegalArgumentException("Unknown rule type: " + rule.get("type"));
            };
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing schema attribute '" + key + "' in " + map);
        }
        return String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        return value == null ? defaultValue : (Boolean) value;
    }

    private static Integer integer(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : ((Number) value).intValue();
    }

    private static BigDecimal decimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : new BigDecimal(value.toString());
    }
}

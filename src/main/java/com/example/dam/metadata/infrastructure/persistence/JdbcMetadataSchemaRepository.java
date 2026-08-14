package com.example.dam.metadata.infrastructure.persistence;

import static com.example.dam.common.persistence.SqlValues.timestamptz;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.domain.schema.AiFieldPolicy;
import com.example.dam.metadata.domain.schema.CrossFieldRule;
import com.example.dam.metadata.domain.schema.FieldRequirement;
import com.example.dam.metadata.domain.schema.MetadataDataType;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataFieldGroup;
import com.example.dam.metadata.domain.schema.MetadataSchemaDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchemaRepository;
import com.example.dam.metadata.domain.schema.ValidationRules;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Repository
class JdbcMetadataSchemaRepository implements MetadataSchemaRepository {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    JdbcMetadataSchemaRepository(JdbcClient jdbc, JsonMapper jsonMapper, Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public Optional<MetadataSchemaDefinition> load() {
        Optional<Integer> version = jdbc.sql("SELECT schema_version FROM metadata_schema_info")
                .query(Integer.class)
                .optional();
        if (version.isEmpty()) {
            return Optional.empty();
        }
        List<MetadataFieldDefinition> fields = jdbc
                .sql("SELECT * FROM metadata_field_definitions ORDER BY position")
                .query(this::mapField)
                .list();
        Map<AssetType, Map<String, FieldRequirement>> requirements = new EnumMap<>(AssetType.class);
        jdbc.sql("SELECT asset_type, field_key, requirement FROM metadata_asset_type_requirements ORDER BY asset_type, field_key")
                .query(rs -> {
                    requirements.computeIfAbsent(AssetType.valueOf(rs.getString("asset_type")), type -> new LinkedHashMap<>())
                            .put(rs.getString("field_key"), FieldRequirement.valueOf(rs.getString("requirement")));
                });
        List<CrossFieldRule> rules = jdbc.sql("SELECT rule_id, rule_type, definition FROM metadata_cross_field_rules ORDER BY rule_id")
                .query(this::mapRule)
                .list();
        List<MetadataFieldGroup> groups = jdbc
                .sql("SELECT group_key, display_name FROM metadata_field_groups ORDER BY position")
                .query((rs, row) -> new MetadataFieldGroup(rs.getString("group_key"), rs.getString("display_name")))
                .list();
        return Optional.of(new MetadataSchemaDefinition(version.get(), fields, requirements, rules, groups));
    }

    @Override
    @Transactional
    public void replace(MetadataSchemaDefinition definition) {
        jdbc.sql("DELETE FROM metadata_cross_field_rules").update();
        jdbc.sql("DELETE FROM metadata_asset_type_requirements").update();
        jdbc.sql("DELETE FROM metadata_field_definitions").update();
        jdbc.sql("DELETE FROM metadata_field_groups").update();

        int groupPosition = 0;
        for (MetadataFieldGroup group : definition.groups()) {
            jdbc.sql("INSERT INTO metadata_field_groups (group_key, display_name, position) VALUES (:key, :name, :position)")
                    .param("key", group.key())
                    .param("name", group.displayName())
                    .param("position", groupPosition++)
                    .update();
        }
        int position = 0;
        for (MetadataFieldDefinition field : definition.fields()) {
            insertField(field, position++);
        }
        definition.requirements().forEach((type, perType) -> perType.forEach((key, requirement) ->
                jdbc.sql("""
                                INSERT INTO metadata_asset_type_requirements (asset_type, field_key, requirement)
                                VALUES (:type, :key, :requirement)""")
                        .param("type", type.name())
                        .param("key", key)
                        .param("requirement", requirement.name())
                        .update()));
        definition.rules().forEach(this::insertRule);

        Instant now = clock.instant();
        jdbc.sql("""
                        INSERT INTO metadata_schema_info (singleton, schema_version, synced_at) VALUES (TRUE, :version, :now)
                        ON CONFLICT (singleton) DO UPDATE SET schema_version = EXCLUDED.schema_version,
                                                              synced_at = EXCLUDED.synced_at""")
                .param("version", definition.version())
                .param("now", timestamptz(now))
                .update();
    }

    private void insertField(MetadataFieldDefinition field, int position) {
        jdbc.sql("""
                        INSERT INTO metadata_field_definitions (field_key, display_name, data_type, required, repeatable,
                               searchable, semantic_search, ai_enrichable, ai_auto_apply, human_approval_required,
                               allowed_values, validation_rules, description, position, field_group)
                        VALUES (:key, :displayName, :dataType, :required, :repeatable, :searchable, :semanticSearch,
                                :aiEnrichable, :aiAutoApply, :humanApprovalRequired, CAST(:allowedValues AS jsonb),
                                CAST(:validationRules AS jsonb), :description, :position, :group)""")
                .param("key", field.key())
                .param("displayName", field.displayName())
                .param("dataType", field.dataType().name())
                .param("required", field.required())
                .param("repeatable", field.repeatable())
                .param("searchable", field.searchable())
                .param("semanticSearch", field.semanticSearch())
                .param("aiEnrichable", field.aiPolicy().enrichable())
                .param("aiAutoApply", field.aiPolicy().autoApply())
                .param("humanApprovalRequired", field.aiPolicy().humanApprovalRequired())
                .param("allowedValues", jsonMapper.writeValueAsString(field.allowedValues()))
                .param("validationRules", rulesJson(field.validationRules()))
                .param("description", field.description())
                .param("position", position)
                .param("group", field.group())
                .update();
    }

    private void insertRule(CrossFieldRule rule) {
        ObjectNode json = jsonMapper.createObjectNode();
        String type = switch (rule) {
            case CrossFieldRule.DateRange range -> {
                json.put("startField", range.startField());
                json.put("endField", range.endField());
                yield "DATE_RANGE";
            }
            case CrossFieldRule.RightsExpiry expiry -> {
                json.put("field", expiry.field());
                json.put("warningWindowDays", expiry.warningWindowDays());
                yield "RIGHTS_EXPIRY";
            }
            case CrossFieldRule.ConditionalRequired conditional -> {
                json.put("whenField", conditional.whenField());
                json.put("whenValue", conditional.whenValue());
                json.put("requiredField", conditional.requiredField());
                yield "CONDITIONAL_REQUIRED";
            }
        };
        jdbc.sql("INSERT INTO metadata_cross_field_rules (rule_id, rule_type, definition) VALUES (:id, :type, CAST(:json AS jsonb))")
                .param("id", rule.id())
                .param("type", type)
                .param("json", jsonMapper.writeValueAsString(json))
                .update();
    }

    private String rulesJson(ValidationRules rules) {
        ObjectNode json = jsonMapper.createObjectNode();
        if (rules.pattern() != null) {
            json.put("pattern", rules.pattern());
        }
        if (rules.minLength() != null) {
            json.put("minLength", rules.minLength());
        }
        if (rules.maxLength() != null) {
            json.put("maxLength", rules.maxLength());
        }
        if (rules.minValue() != null) {
            json.put("minValue", rules.minValue());
        }
        if (rules.maxValue() != null) {
            json.put("maxValue", rules.maxValue());
        }
        if (rules.maxItems() != null) {
            json.put("maxItems", rules.maxItems());
        }
        return jsonMapper.writeValueAsString(json);
    }

    private MetadataFieldDefinition mapField(ResultSet rs, int row) throws SQLException {
        JsonNode rules = jsonMapper.readTree(rs.getString("validation_rules"));
        List<String> allowedValues = jsonMapper.readValue(rs.getString("allowed_values"),
                jsonMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        return new MetadataFieldDefinition(
                rs.getString("field_key"),
                rs.getString("display_name"),
                MetadataDataType.valueOf(rs.getString("data_type")),
                rs.getBoolean("required"),
                rs.getBoolean("repeatable"),
                rs.getBoolean("searchable"),
                rs.getBoolean("semantic_search"),
                allowedValues,
                new ValidationRules(
                        text(rules, "pattern"),
                        integer(rules, "minLength"),
                        integer(rules, "maxLength"),
                        decimal(rules, "minValue"),
                        decimal(rules, "maxValue"),
                        integer(rules, "maxItems")),
                new AiFieldPolicy(rs.getBoolean("ai_enrichable"), rs.getBoolean("ai_auto_apply"),
                        rs.getBoolean("human_approval_required")),
                rs.getString("description"),
                rs.getString("field_group"));
    }

    private CrossFieldRule mapRule(ResultSet rs, int row) throws SQLException {
        String id = rs.getString("rule_id");
        JsonNode json = jsonMapper.readTree(rs.getString("definition"));
        return switch (rs.getString("rule_type")) {
            case "DATE_RANGE" -> new CrossFieldRule.DateRange(id, text(json, "startField"), text(json, "endField"));
            case "RIGHTS_EXPIRY" -> new CrossFieldRule.RightsExpiry(id, text(json, "field"),
                    json.path("warningWindowDays").asInt(0));
            case "CONDITIONAL_REQUIRED" -> new CrossFieldRule.ConditionalRequired(id, text(json, "whenField"),
                    text(json, "whenValue"), text(json, "requiredField"));
            default -> throw new IllegalStateException("Unknown rule type " + rs.getString("rule_type"));
        };
    }

    private static String text(JsonNode node, String property) {
        JsonNode value = node.get(property);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static Integer integer(JsonNode node, String property) {
        JsonNode value = node.get(property);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static BigDecimal decimal(JsonNode node, String property) {
        JsonNode value = node.get(property);
        return value == null || value.isNull() ? null : value.decimalValue();
    }
}

package com.example.dam.metadata.api;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.domain.schema.CrossFieldRule;
import com.example.dam.metadata.domain.schema.MetadataDataType;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.schema.ValidationRules;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MetadataSchemaResponse(
        int version,
        AssetType assetType,
        List<String> requiredFields,
        List<String> recommendedFields,
        List<FieldResponse> fields,
        List<RuleResponse> rules,
        @Schema(description = "Presentation groups in display order") List<GroupResponse> groups) {

    public record FieldResponse(String key, String displayName, MetadataDataType dataType, boolean required,
                                boolean repeatable, boolean searchable, boolean semanticSearch,
                                List<String> allowedValues, ValidationRules validation, AiPolicyResponse ai,
                                String description, String group) {
    }

    public record GroupResponse(String key, String displayName) {
    }

    public record AiPolicyResponse(boolean enrichable, boolean autoApply, boolean humanApprovalRequired) {
    }

    public record RuleResponse(String id, String type, List<String> fields, String description) {
    }

    static MetadataSchemaResponse from(MetadataSchema schema) {
        return new MetadataSchemaResponse(schema.version(), schema.assetType(),
                List.copyOf(schema.requiredFields()), List.copyOf(schema.recommendedFields()),
                schema.fieldDefinitions().stream().map(field -> field(schema, field)).toList(),
                schema.rules().stream().map(MetadataSchemaResponse::rule).toList(),
                schema.groups().stream().map(group -> new GroupResponse(group.key(), group.displayName())).toList());
    }

    private static FieldResponse field(MetadataSchema schema, MetadataFieldDefinition field) {
        return new FieldResponse(field.key(), field.displayName(), field.dataType(), schema.isRequired(field.key()),
                field.repeatable(), field.searchable(), field.semanticSearch(), field.allowedValues(),
                field.validationRules(),
                new AiPolicyResponse(field.aiPolicy().enrichable(), field.aiPolicy().autoApply(),
                        field.aiPolicy().humanApprovalRequired()),
                field.description(), field.group());
    }

    private static RuleResponse rule(CrossFieldRule rule) {
        return switch (rule) {
            case CrossFieldRule.DateRange range -> new RuleResponse(range.id(), "DATE_RANGE",
                    List.of(range.startField(), range.endField()),
                    "%s must not be after %s".formatted(range.startField(), range.endField()));
            case CrossFieldRule.RightsExpiry expiry -> new RuleResponse(expiry.id(), "RIGHTS_EXPIRY",
                    List.of(expiry.field()), "Rights must not be expired; warning %d days before expiry"
                            .formatted(expiry.warningWindowDays()));
            case CrossFieldRule.ConditionalRequired conditional -> new RuleResponse(conditional.id(),
                    "CONDITIONAL_REQUIRED", List.of(conditional.whenField(), conditional.requiredField()),
                    "%s is required when %s is %s".formatted(conditional.requiredField(), conditional.whenField(),
                            conditional.whenValue()));
        };
    }
}

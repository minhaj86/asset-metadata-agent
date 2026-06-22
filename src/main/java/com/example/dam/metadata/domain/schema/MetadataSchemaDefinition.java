package com.example.dam.metadata.domain.schema;

import com.example.dam.asset.domain.AssetType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Complete, versioned metadata schema across all asset types: field definitions, per-asset-type requirements,
 * cross-field rules and the presentation groups of fields. Self-validating so that an inconsistent configuration
 * fails at startup, not at runtime.
 *
 * @param groups declared field groups in display order; {@link MetadataFieldGroup#OTHER} is appended when used
 */
public record MetadataSchemaDefinition(
        int version,
        List<MetadataFieldDefinition> fields,
        Map<AssetType, Map<String, FieldRequirement>> requirements,
        List<CrossFieldRule> rules,
        List<MetadataFieldGroup> groups) {

    public MetadataSchemaDefinition {
        if (version < 1) {
            throw new IllegalArgumentException("Schema version must be positive");
        }
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        rules = Objects.requireNonNull(rules, "rules").stream()
                .sorted(Comparator.comparing(CrossFieldRule::id))
                .toList();
        Map<AssetType, Map<String, FieldRequirement>> copy = new EnumMap<>(AssetType.class);
        Objects.requireNonNull(requirements, "requirements")
                .forEach((type, perType) -> copy.put(type, Collections.unmodifiableMap(new LinkedHashMap<>(perType))));
        requirements = Collections.unmodifiableMap(copy);
        validateConsistency(fields, requirements, rules);
        groups = effectiveGroups(groups == null ? List.of() : groups, fields);
    }

    public Optional<MetadataFieldDefinition> field(String key) {
        return fields.stream().filter(field -> field.key().equals(key)).findFirst();
    }

    /** Resolves the effective schema for one asset type (global + type specific requirements). */
    public MetadataSchema forAssetType(AssetType assetType) {
        Map<String, MetadataFieldDefinition> byKey = new LinkedHashMap<>();
        fields.forEach(field -> byKey.put(field.key(), field));

        Set<String> required = new LinkedHashSet<>();
        fields.stream().filter(MetadataFieldDefinition::required).forEach(field -> required.add(field.key()));
        Set<String> recommended = new LinkedHashSet<>();
        requirements.getOrDefault(assetType, Map.of()).forEach((key, requirement) -> {
            if (requirement == FieldRequirement.REQUIRED) {
                required.add(key);
            } else {
                recommended.add(key);
            }
        });
        recommended.removeAll(required);
        return new MetadataSchema(version, assetType, byKey, required, recommended, rules, groups);
    }

    private static List<MetadataFieldGroup> effectiveGroups(List<MetadataFieldGroup> declared,
                                                            List<MetadataFieldDefinition> fields) {
        List<MetadataFieldGroup> groups = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (MetadataFieldGroup group : declared) {
            if (!keys.add(group.key())) {
                throw new IllegalArgumentException("Duplicate field group: " + group.key());
            }
            groups.add(group);
        }
        for (MetadataFieldDefinition field : fields) {
            if (keys.contains(field.group())) {
                continue;
            }
            if (!MetadataFieldGroup.OTHER.equals(field.group())) {
                throw new IllegalArgumentException("Field " + field.key() + " references undeclared group: " + field.group());
            }
            keys.add(MetadataFieldGroup.OTHER);
            groups.add(MetadataFieldGroup.other());
        }
        return List.copyOf(groups);
    }

    private static void validateConsistency(List<MetadataFieldDefinition> fields,
                                            Map<AssetType, Map<String, FieldRequirement>> requirements,
                                            List<CrossFieldRule> rules) {
        Set<String> keys = new LinkedHashSet<>();
        for (MetadataFieldDefinition field : fields) {
            if (!keys.add(field.key())) {
                throw new IllegalArgumentException("Duplicate field definition: " + field.key());
            }
        }
        requirements.forEach((type, perType) -> perType.keySet().forEach(key -> {
            if (!keys.contains(key)) {
                throw new IllegalArgumentException("Requirement for " + type + " references unknown field: " + key);
            }
        }));
        Set<String> ruleIds = new LinkedHashSet<>();
        for (CrossFieldRule rule : rules) {
            if (!ruleIds.add(rule.id())) {
                throw new IllegalArgumentException("Duplicate rule id: " + rule.id());
            }
            rule.referencedFields().forEach(key -> {
                if (!keys.contains(key)) {
                    throw new IllegalArgumentException("Rule " + rule.id() + " references unknown field: " + key);
                }
            });
        }
    }
}

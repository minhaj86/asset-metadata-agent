package com.example.dam.metadata.domain.schema;

import com.example.dam.asset.domain.AssetType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Effective schema for one asset type. */
public record MetadataSchema(
        int version,
        AssetType assetType,
        Map<String, MetadataFieldDefinition> fields,
        Set<String> requiredFields,
        Set<String> recommendedFields,
        List<CrossFieldRule> rules,
        List<MetadataFieldGroup> groups) {

    public MetadataSchema {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        requiredFields = Collections.unmodifiableSet(new LinkedHashSet<>(requiredFields));
        recommendedFields = Collections.unmodifiableSet(new LinkedHashSet<>(recommendedFields));
        rules = List.copyOf(rules);
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    public Optional<MetadataFieldDefinition> field(String key) {
        return Optional.ofNullable(fields.get(key));
    }

    public List<MetadataFieldDefinition> fieldDefinitions() {
        return List.copyOf(fields.values());
    }

    public boolean isRequired(String key) {
        return requiredFields.contains(key);
    }

    public List<MetadataFieldDefinition> aiEnrichableFields() {
        return fields.values().stream().filter(MetadataFieldDefinition::aiEnrichable).toList();
    }

    public List<MetadataFieldDefinition> searchableFields() {
        return fields.values().stream().filter(MetadataFieldDefinition::searchable).toList();
    }

    public Optional<CrossFieldRule.RightsExpiry> rightsExpiryRule() {
        return rules.stream()
                .filter(CrossFieldRule.RightsExpiry.class::isInstance)
                .map(CrossFieldRule.RightsExpiry.class::cast)
                .findFirst();
    }
}

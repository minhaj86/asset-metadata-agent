package com.example.dam.metadata.domain.schema;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Configurable definition of one metadata field. Business logic never hard-codes field semantics; it reads them
 * from these definitions.
 *
 * @param required       required for every asset type (asset-type specific requirements live in the schema)
 * @param searchable     may be used in structured search filters
 * @param semanticSearch included in the text that is embedded for semantic search
 * @param allowedValues  controlled vocabulary; empty means free text
 * @param group          presentation group key (see {@link MetadataFieldGroup}); no validation semantics
 */
public record MetadataFieldDefinition(
        String key,
        String displayName,
        MetadataDataType dataType,
        boolean required,
        boolean repeatable,
        boolean searchable,
        boolean semanticSearch,
        List<String> allowedValues,
        ValidationRules validationRules,
        AiFieldPolicy aiPolicy,
        String description,
        String group) {

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][A-Za-z0-9]{0,99}");

    public MetadataFieldDefinition {
        Objects.requireNonNull(key, "key");
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid field key (expected lowerCamelCase): " + key);
        }
        displayName = displayName == null || displayName.isBlank() ? key : displayName;
        Objects.requireNonNull(dataType, "dataType");
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        validationRules = validationRules == null ? ValidationRules.NONE : validationRules;
        aiPolicy = aiPolicy == null ? AiFieldPolicy.NOT_ENRICHABLE : aiPolicy;
        description = description == null ? "" : description.strip();
        group = group == null || group.isBlank() ? MetadataFieldGroup.OTHER : group.strip();
        if (!allowedValues.isEmpty() && dataType != MetadataDataType.STRING) {
            throw new IllegalArgumentException("Controlled vocabularies are only supported for STRING fields: " + key);
        }
    }

    public boolean hasControlledVocabulary() {
        return !allowedValues.isEmpty();
    }

    public boolean aiEnrichable() {
        return aiPolicy.enrichable();
    }

    /**
     * Maps a candidate to the canonical vocabulary entry, tolerating case, spaces and hyphens
     * (e.g. {@code "light blue"} -> {@code LIGHT_BLUE}).
     */
    public Optional<String> canonicalAllowedValue(String candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        String normalized = vocabularyKey(candidate);
        return allowedValues.stream().filter(allowed -> vocabularyKey(allowed).equals(normalized)).findFirst();
    }

    private static String vocabularyKey(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
    }
}

package com.example.dam.metadata.domain.schema;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Presentation group of metadata fields (e.g. PRODUCT, VISUAL, RIGHTS). Groups are part of the configurable schema
 * so that user interfaces can organise fields without hard-coding field semantics; they carry no validation rules.
 */
public record MetadataFieldGroup(String key, String displayName) {

    /** Implicit group of fields that do not declare one. */
    public static final String OTHER = "OTHER";

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");

    public MetadataFieldGroup {
        Objects.requireNonNull(key, "key");
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid field group key (expected UPPER_SNAKE_CASE): " + key);
        }
        displayName = displayName == null || displayName.isBlank() ? key : displayName.strip();
    }

    public static MetadataFieldGroup other() {
        return new MetadataFieldGroup(OTHER, "Other");
    }
}

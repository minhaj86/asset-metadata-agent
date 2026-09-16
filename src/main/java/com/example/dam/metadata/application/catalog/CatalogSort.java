package com.example.dam.metadata.application.catalog;

import java.util.Objects;

/**
 * Sort order of the catalog: a fixed column or one of the configured key metadata fields.
 *
 * @param metadataField key metadata field to sort by when {@code column} is {@link Column#METADATA_FIELD}
 */
public record CatalogSort(Column column, String metadataField) {

    public enum Column {
        UPDATED_AT, CREATED_AT, EXTERNAL_ASSET_ID, FILENAME, TYPE, STATUS, COMPLETENESS, QUALITY_SCORE,
        MISSING_REQUIRED, RIGHTS_END, METADATA_FIELD
    }

    public CatalogSort {
        Objects.requireNonNull(column, "column");
        if (column == Column.METADATA_FIELD && (metadataField == null || metadataField.isBlank())) {
            throw new IllegalArgumentException("metadataField is required for METADATA_FIELD sorting");
        }
    }

    public static CatalogSort of(Column column) {
        return new CatalogSort(column, null);
    }

    public static CatalogSort byField(String field) {
        return new CatalogSort(Column.METADATA_FIELD, field);
    }
}

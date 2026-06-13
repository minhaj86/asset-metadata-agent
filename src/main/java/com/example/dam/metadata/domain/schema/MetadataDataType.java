package com.example.dam.metadata.domain.schema;

public enum MetadataDataType {
    /** Short single-line text, optionally constrained by a controlled vocabulary. */
    STRING,
    /** Longer free text such as descriptions and alt text. */
    TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    /** ISO-8601 calendar date. */
    DATE,
    /** Absolute http(s) URL. */
    URL,
    /** Canonical path of an existing taxonomy node, e.g. {@code Fashion > Men > Clothing > Shirts}. */
    TAXONOMY_PATH
}

package com.example.dam.metadata.domain;

/** Provenance of a metadata value. */
public enum MetadataSource {
    MANUAL,
    EMBEDDED,
    AI_GENERATED,
    PRODUCT_SYSTEM,
    DAM,
    DERIVED;

    /** Sources whose values are trusted as authoritative when stored. */
    public boolean isAuthoritative() {
        return this == MANUAL || this == PRODUCT_SYSTEM || this == DAM;
    }
}

package com.example.dam.metadata.domain.validation;

/** Domain port for reference data needed by deterministic validation. */
public interface ReferenceDataLookup {

    boolean taxonomyPathExists(String taxonomyPath);
}

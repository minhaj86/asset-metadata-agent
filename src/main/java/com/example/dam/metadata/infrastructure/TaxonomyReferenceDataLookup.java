package com.example.dam.metadata.infrastructure;

import com.example.dam.metadata.domain.validation.ReferenceDataLookup;
import com.example.dam.taxonomy.application.TaxonomyService;
import org.springframework.stereotype.Component;

/** Adapts the taxonomy module to the metadata domain's reference-data port. */
@Component
class TaxonomyReferenceDataLookup implements ReferenceDataLookup {

    private final TaxonomyService taxonomyService;

    TaxonomyReferenceDataLookup(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @Override
    public boolean taxonomyPathExists(String taxonomyPath) {
        return taxonomyService.pathExists(taxonomyPath);
    }
}

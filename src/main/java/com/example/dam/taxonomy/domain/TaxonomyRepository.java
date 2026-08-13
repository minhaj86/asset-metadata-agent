package com.example.dam.taxonomy.domain;

import java.util.List;

public interface TaxonomyRepository {

    List<TaxonomyNode> findAll();
}

package com.example.dam.taxonomy.domain;

import java.util.List;
import java.util.Objects;

/**
 * Node of the hierarchical product taxonomy.
 *
 * @param path canonical, human-readable path, e.g. {@code Fashion > Men > Clothing > Shirts}
 */
public record TaxonomyNode(
        String id,
        String parentId,
        String name,
        String path,
        int depth,
        List<String> synonyms,
        String description,
        boolean leaf) {

    public static final String PATH_SEPARATOR = " > ";

    public TaxonomyNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        synonyms = synonyms == null ? List.of() : List.copyOf(synonyms);
        description = description == null ? "" : description;
    }

    public List<String> pathSegments() {
        return List.of(path.split(PATH_SEPARATOR));
    }
}

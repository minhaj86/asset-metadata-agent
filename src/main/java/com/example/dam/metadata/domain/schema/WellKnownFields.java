package com.example.dam.metadata.domain.schema;

/**
 * The few fields whose semantics the application must know in code (integration with other systems).
 * Everything else is driven purely by the configurable schema.
 */
public final class WellKnownFields {

    /** Link to the product information system; AI may never set it. */
    public static final String PRODUCT_ID = "productId";
    /** Target field of the taxonomy classification. */
    public static final String TAXONOMY_PATH = "taxonomyPath";

    private WellKnownFields() {
    }
}

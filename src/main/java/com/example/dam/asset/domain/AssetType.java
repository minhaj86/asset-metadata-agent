package com.example.dam.asset.domain;

public enum AssetType {
    PRODUCT_IMAGE,
    CAMPAIGN_IMAGE,
    MODEL_IMAGE,
    VIDEO,
    DOCUMENT,
    DIGITAL_TWIN,
    OTHER;

    /** Visual assets of fashion products are classified into the product taxonomy. */
    public boolean isTaxonomyClassifiable() {
        return this == PRODUCT_IMAGE || this == MODEL_IMAGE || this == CAMPAIGN_IMAGE;
    }
}

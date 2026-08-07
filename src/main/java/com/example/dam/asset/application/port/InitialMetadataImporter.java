package com.example.dam.asset.application.port;

import com.example.dam.asset.domain.Asset;
import com.example.dam.common.domain.Actor;
import java.util.Map;

/**
 * Outbound port used during registration to hand initial descriptive metadata to the metadata module.
 * Keeps the asset module independent of the metadata model (dependency inversion).
 */
public interface InitialMetadataImporter {

    /**
     * Validates and stores the initial metadata of a freshly registered asset.
     *
     * @param rawValues field key to raw JSON-compatible value (string, number, boolean or list)
     * @param source    provenance of the values, e.g. MANUAL, DAM or PRODUCT_SYSTEM
     */
    void importInitialMetadata(Asset asset, Map<String, Object> rawValues, String source, Actor actor);
}

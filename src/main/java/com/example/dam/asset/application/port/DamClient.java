package com.example.dam.asset.application.port;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port to the enterprise DAM that owns the binaries.
 * Vendor specifics stay in the adapter; the domain only sees these neutral records.
 */
public interface DamClient {

    Optional<DamAssetRecord> getAsset(String externalAssetId);

    /** Embedded/technical metadata (EXIF, XMP, IPTC) keyed by namespaced property, e.g. {@code exif:Copyright}. */
    Map<String, String> getMetadata(String externalAssetId);

    /** Writes approved descriptive metadata back to the DAM. */
    void updateMetadata(String externalAssetId, Map<String, String> metadata);

    List<DamAssetRecord> searchAssets(String query, int limit);

    record DamAssetRecord(String externalAssetId, String filename, String mimeType, URI url,
                          Map<String, String> embeddedMetadata) {
    }

    /** Connectivity of the DAM; adapters should answer quickly (cached or lightweight ping). */
    DamHealth health();

    /**
     * @param system name of the DAM product or adapter, e.g. {@code Mock DAM}
     */
    record DamHealth(boolean available, String system, String detail) {
    }
}

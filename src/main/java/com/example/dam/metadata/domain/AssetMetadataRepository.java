package com.example.dam.metadata.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistence port for current metadata values. */
public interface AssetMetadataRepository {

    List<AssetMetadata> findByAssetId(UUID assetId);

    Map<UUID, List<AssetMetadata>> findByAssetIds(Collection<UUID> assetIds);

    void upsert(AssetMetadata metadata);

    boolean delete(UUID assetId, String fieldKey);

    /** Number of assets per (scalar) value of a field, e.g. per taxonomy path. */
    Map<String, Long> countAssetsByValue(String fieldKey);
}

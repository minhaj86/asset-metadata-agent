package com.example.dam.metadata.application.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port of the {@link AssetMetadataSummary} read model. */
public interface AssetSummaryRepository {

    void upsert(AssetMetadataSummary summary);

    Optional<AssetMetadataSummary> findByAssetId(UUID assetId);

    /** Assets that have no summary yet (e.g. created before the read model existed). */
    List<UUID> findAssetIdsWithoutSummary(int limit);
}

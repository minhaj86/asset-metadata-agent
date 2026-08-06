package com.example.dam.asset.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for asset references. */
public interface AssetRepository {

    void insert(Asset asset);

    Optional<Asset> findById(UUID id);

    Optional<Asset> findByExternalAssetId(String externalAssetId);

    List<Asset> findAllById(List<UUID> ids);

    List<Asset> findPage(AssetType type, int offset, int limit);

    /** Loads the asset and acquires a row lock for the current transaction (serialises metadata writes). */
    Optional<Asset> lockById(UUID id);

    void updateStatus(UUID id, AssetStatus status, Instant now);

    /** Increments and returns the metadata revision. */
    long incrementMetadataRevision(UUID id, Instant now);
}

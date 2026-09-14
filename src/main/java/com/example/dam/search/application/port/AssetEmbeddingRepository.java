package com.example.dam.search.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for asset embeddings (pgvector). */
public interface AssetEmbeddingRepository {

    /** Stores the embedding unless a newer revision is already indexed. */
    void upsert(UUID assetId, float[] vector, String text, String model, long metadataRevision, Instant indexedAt);

    Optional<float[]> findVector(UUID assetId);

    /** Assets without an embedding, with an outdated revision or embedded by another model. */
    List<UUID> findStaleAssetIds(String currentModel, int limit);

    List<Neighbour> findNearest(UUID assetId, int limit);

    /** Index coverage: assets with an embedding of the current model and revision, and all assets. */
    Coverage coverage(String currentModel);

    record Coverage(long indexedAssets, long totalAssets) {
    }

    record Neighbour(UUID assetId, double similarity) {
    }
}

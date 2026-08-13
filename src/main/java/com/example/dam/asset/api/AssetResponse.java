package com.example.dam.asset.api;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.asset.domain.AssetType;
import java.time.Instant;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        String externalAssetId,
        String filename,
        String mimeType,
        String assetUrl,
        AssetType type,
        AssetStatus status,
        long metadataRevision,
        Instant createdAt,
        Instant updatedAt) {

    static AssetResponse from(Asset asset) {
        return new AssetResponse(asset.id(), asset.externalAssetId(), asset.filename(), asset.mimeType(),
                asset.assetUrl().toString(), asset.type(), asset.status(), asset.metadataRevision(), asset.createdAt(),
                asset.updatedAt());
    }
}

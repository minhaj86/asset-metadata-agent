package com.example.dam.asset.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Asset aggregate: a reference to a binary owned by an external DAM. The application never stores the binary.
 * Descriptive metadata lives in the metadata module; {@code metadataRevision} increments on every metadata change
 * and lets derived artefacts (e.g. search embeddings) detect staleness.
 */
public record Asset(
        UUID id,
        String externalAssetId,
        String filename,
        String mimeType,
        URI assetUrl,
        AssetType type,
        AssetStatus status,
        long metadataRevision,
        Instant createdAt,
        Instant updatedAt) {

    private static final Set<String> SUPPORTED_URL_SCHEMES = Set.of("http", "https", "s3", "dam");

    public Asset {
        Objects.requireNonNull(id, "id");
        requireText(externalAssetId, "externalAssetId", 100);
        requireText(filename, "filename", 512);
        requireText(mimeType, "mimeType", 127);
        Objects.requireNonNull(assetUrl, "assetUrl");
        if (!assetUrl.isAbsolute() || !SUPPORTED_URL_SCHEMES.contains(assetUrl.getScheme().toLowerCase())) {
            throw new IllegalArgumentException("assetUrl must be an absolute http(s), s3 or dam URI: " + assetUrl);
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (metadataRevision < 0) {
            throw new IllegalArgumentException("metadataRevision must not be negative");
        }
    }

    public static Asset register(String externalAssetId, String filename, String mimeType, URI assetUrl,
                                 AssetType type, Instant now) {
        return new Asset(UUID.randomUUID(), externalAssetId.trim(), filename.trim(), mimeType.trim(), assetUrl, type,
                AssetStatus.DRAFT, 0, now, now);
    }

    public Asset withStatus(AssetStatus newStatus, Instant now) {
        if (newStatus == status) {
            return this;
        }
        return new Asset(id, externalAssetId, filename, mimeType, assetUrl, type, newStatus, metadataRevision,
                createdAt, now);
    }

    private static void requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
        }
    }
}

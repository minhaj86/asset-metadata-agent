package com.example.dam.metadata.domain;

import com.example.dam.metadata.domain.value.MetadataValue;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Immutable view of all current metadata entries of one asset. */
public record AssetMetadataSnapshot(UUID assetId, Map<String, AssetMetadata> entries) {

    public AssetMetadataSnapshot {
        Objects.requireNonNull(assetId, "assetId");
        entries = Collections.unmodifiableMap(new TreeMap<>(entries));
    }

    public static AssetMetadataSnapshot of(UUID assetId, Collection<AssetMetadata> entries) {
        Map<String, AssetMetadata> byKey = new LinkedHashMap<>();
        entries.forEach(entry -> byKey.put(entry.fieldKey(), entry));
        return new AssetMetadataSnapshot(assetId, byKey);
    }

    public static AssetMetadataSnapshot empty(UUID assetId) {
        return new AssetMetadataSnapshot(assetId, Map.of());
    }

    public Optional<AssetMetadata> entry(String fieldKey) {
        return Optional.ofNullable(entries.get(fieldKey));
    }

    public Optional<MetadataValue> value(String fieldKey) {
        return entry(fieldKey).map(AssetMetadata::value);
    }

    public boolean has(String fieldKey) {
        return entries.containsKey(fieldKey);
    }

    public Map<String, MetadataValue> values() {
        Map<String, MetadataValue> values = new LinkedHashMap<>();
        entries.forEach((key, entry) -> values.put(key, entry.value()));
        return Collections.unmodifiableMap(values);
    }
}

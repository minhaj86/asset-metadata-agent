package com.example.dam.metadata.application;

import java.util.Set;
import java.util.UUID;

/** Published (once per transaction) when the metadata of an asset changed; consumed after commit. */
public record MetadataChangedEvent(UUID assetId, Set<String> fieldKeys, long metadataRevision) {

    public MetadataChangedEvent {
        fieldKeys = Set.copyOf(fieldKeys);
    }
}

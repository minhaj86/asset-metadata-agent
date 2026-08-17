package com.example.dam.metadata.api;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.MetadataService.AssetMetadataView;
import java.util.List;
import java.util.UUID;

public record AssetMetadataResponse(
        UUID assetId,
        String externalAssetId,
        AssetType assetType,
        AssetStatus assetStatus,
        int schemaVersion,
        List<MetadataEntryResponse> metadata) {

    static AssetMetadataResponse from(AssetMetadataView view) {
        return new AssetMetadataResponse(view.asset().id(), view.asset().externalAssetId(), view.asset().type(),
                view.asset().status(), view.schema().version(),
                view.metadata().entries().values().stream()
                        .map(entry -> MetadataEntryResponse.from(entry, view.origins().get(entry.fieldKey())))
                        .toList());
    }
}

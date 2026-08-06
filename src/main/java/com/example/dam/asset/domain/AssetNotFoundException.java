package com.example.dam.asset.domain;

import com.example.dam.common.domain.ResourceNotFoundException;

public class AssetNotFoundException extends ResourceNotFoundException {

    public AssetNotFoundException(Object assetReference) {
        super("ASSET_NOT_FOUND", "Asset not found: " + assetReference);
    }
}

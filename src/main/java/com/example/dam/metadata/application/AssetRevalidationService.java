package com.example.dam.metadata.application;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetNotFoundException;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.metadata.application.MetadataValidationService.AssetValidation;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-runs deterministic validation and refreshes the derived asset status (e.g. after usage rights expired).
 */
@Service
public class AssetRevalidationService {

    private final AssetRepository assetRepository;
    private final MetadataValidationService validationService;
    private final AssetStatusService statusService;

    public AssetRevalidationService(AssetRepository assetRepository, MetadataValidationService validationService,
                                    AssetStatusService statusService) {
        this.assetRepository = assetRepository;
        this.validationService = validationService;
        this.statusService = statusService;
    }

    public record Revalidation(AssetValidation validation, AssetStatus status) {
    }

    @Transactional
    public Revalidation revalidate(UUID assetId) {
        Asset asset = assetRepository.lockById(assetId).orElseThrow(() -> new AssetNotFoundException(assetId));
        AssetStatus status = statusService.refresh(asset);
        return new Revalidation(validationService.validate(asset), status);
    }
}

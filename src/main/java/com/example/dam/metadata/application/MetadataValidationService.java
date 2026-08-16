package com.example.dam.metadata.application;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetNotFoundException;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.quality.CompletenessCalculator;
import com.example.dam.metadata.domain.quality.CompletenessScore;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.validation.MetadataValidator;
import com.example.dam.metadata.domain.validation.ValidationReport;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deterministic validation of an asset's metadata. No LLM involved. */
@Service
public class MetadataValidationService {

    private final AssetRepository assetRepository;
    private final AssetMetadataRepository metadataRepository;
    private final MetadataSchemaService schemaService;
    private final MetadataValidator validator;
    private final CompletenessCalculator completenessCalculator;
    private final Clock clock;

    public MetadataValidationService(AssetRepository assetRepository, AssetMetadataRepository metadataRepository,
                                     MetadataSchemaService schemaService, MetadataValidator validator,
                                     CompletenessCalculator completenessCalculator, Clock clock) {
        this.assetRepository = assetRepository;
        this.metadataRepository = metadataRepository;
        this.schemaService = schemaService;
        this.validator = validator;
        this.completenessCalculator = completenessCalculator;
        this.clock = clock;
    }

    public record AssetValidation(Asset asset, int schemaVersion, AssetMetadataSnapshot metadata,
                                  ValidationReport report, CompletenessScore completeness) {
    }

    @Transactional(readOnly = true)
    public AssetValidation validate(UUID assetId) {
        return validate(assetRepository.findById(assetId).orElseThrow(() -> new AssetNotFoundException(assetId)));
    }

    @Transactional(readOnly = true)
    public AssetValidation validate(Asset asset) {
        MetadataSchema schema = schemaService.schemaFor(asset.type());
        AssetMetadataSnapshot snapshot = AssetMetadataSnapshot.of(asset.id(), metadataRepository.findByAssetId(asset.id()));
        ValidationReport report = validator.validate(schema, snapshot, LocalDate.now(clock));
        return new AssetValidation(asset, schema.version(), snapshot, report,
                completenessCalculator.calculate(schema, snapshot));
    }
}

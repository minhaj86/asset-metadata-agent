package com.example.dam.metadata.application;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.metadata.application.MetadataValidationService.AssetValidation;
import com.example.dam.metadata.application.catalog.AssetMetadataSummary;
import com.example.dam.metadata.application.catalog.AssetSummaryRepository;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import com.example.dam.metadata.domain.proposal.MetadataProposalRepository;
import com.example.dam.metadata.domain.quality.QualityScoreCalculator;
import com.example.dam.metadata.domain.value.DateValue;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives the asset lifecycle status from open review work and metadata validity, and maintains the asset's
 * metadata summary read model in the same transaction. Called after every metadata change.
 */
@Component
public class AssetStatusService {

    private final AssetRepository assetRepository;
    private final MetadataProposalRepository proposalRepository;
    private final MetadataValidationService validationService;
    private final MetadataSchemaService schemaService;
    private final QualityScoreCalculator scoreCalculator;
    private final AssetSummaryRepository summaryRepository;
    private final Clock clock;

    AssetStatusService(AssetRepository assetRepository, MetadataProposalRepository proposalRepository,
                       MetadataValidationService validationService, MetadataSchemaService schemaService,
                       QualityScoreCalculator scoreCalculator, AssetSummaryRepository summaryRepository, Clock clock) {
        this.assetRepository = assetRepository;
        this.proposalRepository = proposalRepository;
        this.validationService = validationService;
        this.schemaService = schemaService;
        this.scoreCalculator = scoreCalculator;
        this.summaryRepository = summaryRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AssetStatus refresh(Asset asset) {
        AssetValidation validation = validationService.validate(asset);
        long pendingReview = proposalRepository.countOpenByAssetAndRoute(asset.id(), ApprovalRoute.HUMAN_REVIEW);
        long lowConfidence = proposalRepository.countOpenByAssetAndRoute(asset.id(), ApprovalRoute.LOW_CONFIDENCE);
        summaryRepository.upsert(summary(asset, validation, pendingReview, lowConfidence));

        if (asset.status() == AssetStatus.ARCHIVED) {
            return AssetStatus.ARCHIVED;
        }
        AssetStatus status = pendingReview > 0 ? AssetStatus.IN_REVIEW
                : validation.report().isValid() ? AssetStatus.READY : AssetStatus.DRAFT;
        assetRepository.updateStatus(asset.id(), status, clock.instant());
        return status;
    }

    private AssetMetadataSummary summary(Asset asset, AssetValidation validation, long pendingReview,
                                         long lowConfidence) {
        int aiValues = (int) validation.metadata().entries().values().stream()
                .filter(entry -> entry.source() == MetadataSource.AI_GENERATED)
                .count();
        return new AssetMetadataSummary(asset.id(), validation.schemaVersion(), validation.report().isValid(),
                validation.completeness().overallPercentage(), validation.completeness().requiredPercentage(),
                scoreCalculator.score(validation.completeness(), validation.report()),
                validation.completeness().missingRequired(), validation.report().errors().size(),
                validation.report().warnings().size(), aiValues, (int) pendingReview, (int) lowConfidence,
                usageRightsEnd(asset, validation), clock.instant());
    }

    private LocalDate usageRightsEnd(Asset asset, AssetValidation validation) {
        return schemaService.schemaFor(asset.type()).rightsExpiryRule()
                .flatMap(rule -> validation.metadata().entry(rule.field()))
                .map(AssetMetadata::value)
                .filter(DateValue.class::isInstance)
                .map(value -> ((DateValue) value).value())
                .orElse(null);
    }
}

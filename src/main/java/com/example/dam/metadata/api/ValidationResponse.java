package com.example.dam.metadata.api;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.MetadataValidationService.AssetValidation;
import java.util.List;
import java.util.UUID;

public record ValidationResponse(
        UUID assetId,
        AssetType assetType,
        AssetStatus assetStatus,
        int schemaVersion,
        boolean valid,
        double completenessPercentage,
        double requiredCompletenessPercentage,
        List<String> missingRequiredFields,
        List<String> missingRecommendedFields,
        List<IssueResponse> errors,
        List<IssueResponse> warnings) {

    static ValidationResponse from(AssetValidation validation, AssetStatus status) {
        return new ValidationResponse(validation.asset().id(), validation.asset().type(), status,
                validation.schemaVersion(), validation.report().isValid(),
                validation.completeness().overallPercentage(), validation.completeness().requiredPercentage(),
                validation.completeness().missingRequired(), validation.completeness().missingRecommended(),
                validation.report().errors().stream().map(IssueResponse::from).toList(),
                validation.report().warnings().stream().map(IssueResponse::from).toList());
    }
}

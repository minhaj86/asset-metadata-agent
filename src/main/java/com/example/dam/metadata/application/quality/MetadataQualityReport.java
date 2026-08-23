package com.example.dam.metadata.application.quality;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.quality.SemanticQualityAnalyzer.QualitySuggestion;
import com.example.dam.metadata.domain.validation.ValidationIssue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Combined quality report. Score and completeness are computed deterministically in Java; the AI part only
 * contributes softer, clearly separated suggestions.
 */
public record MetadataQualityReport(
        UUID assetId,
        AssetType assetType,
        int schemaVersion,
        int qualityScore,
        double completenessPercentage,
        double requiredCompletenessPercentage,
        List<String> missingRequiredFields,
        List<ValidationIssue> deterministicErrors,
        List<ValidationIssue> warnings,
        AiStatus aiStatus,
        List<QualitySuggestion> aiSuggestions,
        Double aiConfidence,
        String aiSummary,
        String aiModel,
        Instant assessedAt) {

    public enum AiStatus { COMPLETED, SKIPPED, UNAVAILABLE }

    public MetadataQualityReport {
        missingRequiredFields = List.copyOf(missingRequiredFields);
        deterministicErrors = List.copyOf(deterministicErrors);
        warnings = List.copyOf(warnings);
        aiSuggestions = List.copyOf(aiSuggestions);
    }
}

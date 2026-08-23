package com.example.dam.metadata.api;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.quality.MetadataQualityReport;
import com.example.dam.metadata.application.quality.MetadataQualityReport.AiStatus;
import com.example.dam.metadata.application.quality.SemanticQualityAnalyzer.QualitySuggestion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Deterministic findings (computed in Java) plus clearly separated AI suggestions")
public record QualityReportResponse(
        UUID assetId,
        AssetType assetType,
        int schemaVersion,
        @Schema(description = "0-100, computed deterministically") int qualityScore,
        double completenessPercentage,
        double requiredCompletenessPercentage,
        List<String> missingRequiredFields,
        List<IssueResponse> deterministicErrors,
        List<IssueResponse> warnings,
        AiStatus aiStatus,
        List<AiSuggestionResponse> aiSuggestions,
        @Schema(description = "Confidence of the AI analysis") Double confidence,
        String aiSummary,
        String aiModel,
        Instant assessedAt) {

    public record AiSuggestionResponse(String field, String category, String severity, String message,
                                       String suggestedImprovement) {
        static AiSuggestionResponse from(QualitySuggestion suggestion) {
            return new AiSuggestionResponse(suggestion.field(), suggestion.category(), suggestion.severity(),
                    suggestion.message(), suggestion.suggestedImprovement());
        }
    }

    static QualityReportResponse from(MetadataQualityReport report) {
        return new QualityReportResponse(report.assetId(), report.assetType(), report.schemaVersion(),
                report.qualityScore(), report.completenessPercentage(), report.requiredCompletenessPercentage(),
                report.missingRequiredFields(),
                report.deterministicErrors().stream().map(IssueResponse::from).toList(),
                report.warnings().stream().map(IssueResponse::from).toList(),
                report.aiStatus(),
                report.aiSuggestions().stream().map(AiSuggestionResponse::from).toList(),
                report.aiConfidence(), report.aiSummary(), report.aiModel(), report.assessedAt());
    }
}

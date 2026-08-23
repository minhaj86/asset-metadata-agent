package com.example.dam.metadata.application.quality;

import com.example.dam.asset.domain.Asset;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.validation.ValidationIssue;
import java.util.List;

/**
 * Port for AI-assisted, semantic quality analysis (weak descriptions, poor alt text, irrelevant keywords,
 * ambiguous classification, inconsistent semantics). Deterministic checks are never delegated to it.
 */
public interface SemanticQualityAnalyzer {

    SemanticQualityAssessment analyze(SemanticQualityRequest request);

    record SemanticQualityRequest(Asset asset, MetadataSchema schema, AssetMetadataSnapshot metadata,
                                  List<ValidationIssue> deterministicFindings) {
        public SemanticQualityRequest {
            deterministicFindings = List.copyOf(deterministicFindings);
        }
    }

    /**
     * @param confidence the analyser's confidence in its own findings (0..1)
     */
    record SemanticQualityAssessment(List<QualitySuggestion> suggestions, double confidence, String summary,
                                     String model) {
        public SemanticQualityAssessment {
            suggestions = List.copyOf(suggestions);
        }
    }

    /**
     * @param category e.g. WEAK_DESCRIPTION, POOR_ALT_TEXT, IRRELEVANT_KEYWORDS, AMBIGUOUS_CLASSIFICATION,
     *                 INCONSISTENT_SEMANTICS
     */
    record QualitySuggestion(String field, String category, String severity, String message,
                             String suggestedImprovement) {
    }
}

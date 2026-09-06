package com.example.dam.ai.quality;

import com.example.dam.ai.agent.QualityReviewOutput;
import com.example.dam.metadata.application.quality.SemanticQualityAnalyzer.QualitySuggestion;
import com.example.dam.metadata.application.quality.SemanticQualityAnalyzer.SemanticQualityAssessment;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Normalises the quality agent's output (categories, severities, confidence) into the application type. */
public final class QualityReviewMapper {

    private static final Set<String> CATEGORIES = Set.of("WEAK_DESCRIPTION", "POOR_ALT_TEXT", "IRRELEVANT_KEYWORDS",
            "AMBIGUOUS_CLASSIFICATION", "INCONSISTENT_SEMANTICS");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final int MAX_FINDINGS = 20;

    private QualityReviewMapper() {
    }

    public static SemanticQualityAssessment toAssessment(QualityReviewOutput output, String model) {
        if (output == null) {
            return null;
        }
        List<QualitySuggestion> suggestions = output.findings() == null ? List.of() : output.findings().stream()
                .filter(Objects::nonNull)
                .filter(finding -> finding.message() != null && !finding.message().isBlank())
                .limit(MAX_FINDINGS)
                .map(finding -> new QualitySuggestion(finding.field(), normalize(finding.category(), CATEGORIES, "OTHER"),
                        normalize(finding.severity(), SEVERITIES, "MEDIUM"), finding.message().strip(),
                        finding.suggestedImprovement()))
                .toList();
        double confidence = Double.isNaN(output.confidence()) ? 0.0 : Math.clamp(output.confidence(), 0.0, 1.0);
        return new SemanticQualityAssessment(suggestions, confidence, output.summary(), model);
    }

    private static String normalize(String value, Set<String> allowed, String fallback) {
        if (value == null) {
            return fallback;
        }
        String upper = value.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        return allowed.contains(upper) ? upper : fallback;
    }
}

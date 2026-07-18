package com.example.dam.metadata.domain.quality;

import com.example.dam.metadata.domain.validation.IssueCode;
import com.example.dam.metadata.domain.validation.ValidationIssue;
import com.example.dam.metadata.domain.validation.ValidationReport;
import java.util.Set;

/**
 * Deterministic 0-100 quality score: weighted completeness minus penalties for validation findings.
 * Missing-field findings are not penalised twice because completeness already reflects them.
 */
public final class QualityScoreCalculator {

    private static final double REQUIRED_WEIGHT = 0.7;
    private static final double RECOMMENDED_WEIGHT = 0.3;
    private static final int ERROR_PENALTY = 15;
    private static final int WARNING_PENALTY = 3;
    private static final Set<IssueCode> COMPLETENESS_CODES =
            Set.of(IssueCode.MISSING_REQUIRED_FIELD, IssueCode.MISSING_RECOMMENDED_FIELD);

    public int score(CompletenessScore completeness, ValidationReport report) {
        double base = completeness.recommendedTotal() == 0
                ? completeness.requiredPercentage()
                : REQUIRED_WEIGHT * completeness.requiredPercentage()
                        + RECOMMENDED_WEIGHT * completeness.recommendedPercentage();
        long errors = report.issues().stream().filter(ValidationIssue::isError).filter(this::penalised).count();
        long warnings = report.issues().stream().filter(issue -> !issue.isError()).filter(this::penalised).count();
        double score = base - errors * ERROR_PENALTY - warnings * WARNING_PENALTY;
        return (int) Math.round(Math.clamp(score, 0.0, 100.0));
    }

    private boolean penalised(ValidationIssue issue) {
        return !COMPLETENESS_CODES.contains(issue.code());
    }
}

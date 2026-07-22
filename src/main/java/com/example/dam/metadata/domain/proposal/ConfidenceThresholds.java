package com.example.dam.metadata.domain.proposal;

/**
 * Confidence thresholds for AI proposals. Schema safety rules always take precedence over confidence.
 *
 * @param autoApprove proposals at or above may be auto-approved when the schema permits it
 * @param humanReview proposals below are kept as low-confidence suggestions only
 */
public record ConfidenceThresholds(double autoApprove, double humanReview) {

    public ConfidenceThresholds {
        if (humanReview < 0.0 || autoApprove > 1.0 || humanReview > autoApprove) {
            throw new IllegalArgumentException(
                    "Expected 0 <= humanReview <= autoApprove <= 1 but got %s / %s".formatted(humanReview, autoApprove));
        }
    }
}

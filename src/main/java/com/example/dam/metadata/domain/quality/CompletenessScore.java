package com.example.dam.metadata.domain.quality;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Deterministic completeness of an asset's metadata relative to its schema. */
public record CompletenessScore(
        int requiredTotal,
        int requiredPresent,
        int recommendedTotal,
        int recommendedPresent,
        List<String> missingRequired,
        List<String> missingRecommended) {

    public CompletenessScore {
        missingRequired = List.copyOf(missingRequired);
        missingRecommended = List.copyOf(missingRecommended);
    }

    public double requiredPercentage() {
        return percentage(requiredPresent, requiredTotal);
    }

    public double recommendedPercentage() {
        return percentage(recommendedPresent, recommendedTotal);
    }

    /** Share of all required and recommended fields that are populated. */
    public double overallPercentage() {
        return percentage(requiredPresent + recommendedPresent, requiredTotal + recommendedTotal);
    }

    private static double percentage(int present, int total) {
        if (total == 0) {
            return 100.0;
        }
        return BigDecimal.valueOf(present * 100.0 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}

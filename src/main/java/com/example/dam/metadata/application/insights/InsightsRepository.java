package com.example.dam.metadata.application.insights;

import com.example.dam.asset.domain.AssetStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Aggregation port for operational metadata insights. All figures are computed from stored facts. */
public interface InsightsRepository {

    AssetFigures assetFigures(LocalDate today, LocalDate within7Days, LocalDate withinWarningWindow);

    ProposalFigures proposalFigures();

    RunFigures runFigures(Instant startOfToday);

    /**
     * @param completenessBuckets assets per completeness bucket, in the order of {@link #COMPLETENESS_BUCKETS}
     * @param qualityBuckets      assets per quality score bucket, in the order of {@link #QUALITY_BUCKETS}
     */
    record AssetFigures(long total, long missingRequired, Double averageQualityScore, Double averageCompleteness,
                        Map<AssetStatus, Long> byStatus, List<Long> completenessBuckets, List<Long> qualityBuckets,
                        long rightsMissing, long rightsExpired, long rightsExpiringWithin7Days,
                        long rightsExpiringWithinWarningWindow) {
    }

    record ProposalFigures(long pendingHumanReview, long pendingLowConfidence, long autoApproved, long humanApproved,
                           long humanRejected, long rejectedByPolicy, long superseded) {
    }

    record RunFigures(long runsToday, long failedToday, long active) {
    }

    /** Lower bounds (inclusive) of the completeness buckets 95-100, 80-94, 60-79 and below 60. */
    List<Integer> COMPLETENESS_BUCKETS = List.of(95, 80, 60, 0);

    /** Lower bounds (inclusive) of the quality buckets 90-100, 70-89, 50-69 and below 50. */
    List<Integer> QUALITY_BUCKETS = List.of(90, 70, 50, 0);
}

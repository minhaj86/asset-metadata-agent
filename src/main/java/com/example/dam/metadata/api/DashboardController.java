package com.example.dam.metadata.api;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.metadata.application.insights.DashboardService;
import com.example.dam.metadata.application.insights.DashboardService.Dashboard;
import com.example.dam.metadata.application.insights.InsightsRepository;
import com.example.dam.metadata.application.insights.InsightsRepository.AssetFigures;
import com.example.dam.metadata.application.insights.InsightsRepository.ProposalFigures;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Dashboard", description = "Operational metadata insights")
class DashboardController {

    private final DashboardService dashboardService;

    DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard")
    @Operation(summary = "Metadata health, review backlog, usage rights and AI workflow activity")
    DashboardResponse dashboard() {
        return DashboardResponse.from(dashboardService.dashboard());
    }

    record DashboardResponse(Instant generatedAt, AssetsResponse assets, ProposalsResponse proposals,
                             RightsResponse rights, EnrichmentActivityResponse enrichment,
                             List<BucketResponse> completenessDistribution, List<BucketResponse> qualityDistribution) {

        static DashboardResponse from(Dashboard dashboard) {
            AssetFigures assets = dashboard.assets();
            ProposalFigures proposals = dashboard.proposals();
            return new DashboardResponse(dashboard.generatedAt(),
                    new AssetsResponse(assets.total(), assets.missingRequired(), assets.averageQualityScore(),
                            assets.averageCompleteness(), assets.byStatus()),
                    new ProposalsResponse(proposals.pendingHumanReview() + proposals.pendingLowConfidence(),
                            proposals.pendingHumanReview(), proposals.pendingLowConfidence(),
                            new OutcomesResponse(proposals.autoApproved(), proposals.humanApproved(),
                                    proposals.humanRejected(), proposals.rejectedByPolicy(), proposals.superseded(),
                                    proposals.pendingHumanReview() + proposals.pendingLowConfidence())),
                    new RightsResponse(dashboard.rightsWarningWindowDays(), assets.rightsExpiringWithin7Days(),
                            assets.rightsExpiringWithinWarningWindow(), assets.rightsExpired(), assets.rightsMissing()),
                    new EnrichmentActivityResponse(dashboard.runs().runsToday(), dashboard.runs().failedToday(),
                            dashboard.runs().active(),
                            dashboard.recentRuns().stream().map(WorkflowRunResponse::from).toList()),
                    buckets(InsightsRepository.COMPLETENESS_BUCKETS, assets.completenessBuckets()),
                    buckets(InsightsRepository.QUALITY_BUCKETS, assets.qualityBuckets()));
        }

        private static List<BucketResponse> buckets(List<Integer> lowerBounds, List<Long> counts) {
            List<BucketResponse> buckets = new ArrayList<>();
            for (int i = 0; i < lowerBounds.size(); i++) {
                int min = lowerBounds.get(i);
                int max = i == 0 ? 100 : lowerBounds.get(i - 1) - 1;
                String label = min == 0 ? "<" + lowerBounds.get(i - 1) + "%" : min + "-" + max + "%";
                buckets.add(new BucketResponse(label, min, max, counts.get(i)));
            }
            return buckets;
        }
    }

    record AssetsResponse(long total, long missingRequired, Double averageQualityScore, Double averageCompleteness,
                          Map<AssetStatus, Long> byStatus) {
    }

    record ProposalsResponse(long pending, long pendingHumanReview, long pendingLowConfidence,
                             OutcomesResponse outcomes) {
    }

    record OutcomesResponse(long autoApproved, long humanApproved, long humanRejected, long rejectedByPolicy,
                            long superseded, long pending) {
    }

    record RightsResponse(int warningWindowDays, long expiringWithin7Days, long expiringWithinWarningWindow,
                          long expired, long missing) {
    }

    record EnrichmentActivityResponse(long runsToday, long failedToday, long active, List<WorkflowRunResponse> recentRuns) {
    }

    record BucketResponse(String label, int min, int max, long count) {
    }
}

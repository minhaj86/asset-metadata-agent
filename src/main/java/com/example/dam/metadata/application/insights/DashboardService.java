package com.example.dam.metadata.application.insights;

import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.application.enrichment.EnrichmentRun;
import com.example.dam.metadata.application.enrichment.EnrichmentRunTracker;
import com.example.dam.metadata.application.insights.InsightsRepository.AssetFigures;
import com.example.dam.metadata.application.insights.InsightsRepository.ProposalFigures;
import com.example.dam.metadata.application.insights.InsightsRepository.RunFigures;
import com.example.dam.metadata.domain.schema.CrossFieldRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operational dashboard: metadata health, review backlog, usage rights and AI workflow activity. */
@Service
public class DashboardService {

    private static final int RECENT_RUNS = 8;
    private static final int DEFAULT_WARNING_DAYS = 30;

    private final InsightsRepository repository;
    private final EnrichmentRunTracker runTracker;
    private final MetadataSchemaService schemaService;
    private final Clock clock;

    public DashboardService(InsightsRepository repository, EnrichmentRunTracker runTracker,
                            MetadataSchemaService schemaService, Clock clock) {
        this.repository = repository;
        this.runTracker = runTracker;
        this.schemaService = schemaService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        LocalDate today = LocalDate.now(clock);
        int warningDays = schemaService.definition().rules().stream()
                .filter(CrossFieldRule.RightsExpiry.class::isInstance)
                .map(rule -> ((CrossFieldRule.RightsExpiry) rule).warningWindowDays())
                .findFirst()
                .orElse(DEFAULT_WARNING_DAYS);
        Instant startOfToday = today.atStartOfDay(clock.getZone()).toInstant();
        return new Dashboard(clock.instant(), warningDays,
                repository.assetFigures(today, today.plusDays(7), today.plusDays(warningDays)),
                repository.proposalFigures(), repository.runFigures(startOfToday),
                runTracker.recent(null, null, RECENT_RUNS));
    }

    public record Dashboard(Instant generatedAt, int rightsWarningWindowDays, AssetFigures assets,
                            ProposalFigures proposals, RunFigures runs, List<EnrichmentRun> recentRuns) {
    }
}

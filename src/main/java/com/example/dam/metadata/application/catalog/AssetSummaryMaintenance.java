package com.example.dam.metadata.application.catalog;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.metadata.application.AssetRevalidationService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the summary read model complete and current: computes missing summaries at startup (e.g. after an
 * upgrade) and periodically re-validates all assets, because some findings depend on the date (expiring rights).
 */
@Component
class AssetSummaryMaintenance {

    private static final Logger log = LoggerFactory.getLogger(AssetSummaryMaintenance.class);
    private static final int BATCH_SIZE = 200;

    private final AssetSummaryRepository summaryRepository;
    private final AssetRepository assetRepository;
    private final AssetRevalidationService revalidationService;

    AssetSummaryMaintenance(AssetSummaryRepository summaryRepository, AssetRepository assetRepository,
                            AssetRevalidationService revalidationService) {
        this.summaryRepository = summaryRepository;
        this.assetRepository = assetRepository;
        this.revalidationService = revalidationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    void backfillMissingSummaries() {
        List<UUID> missing = summaryRepository.findAssetIdsWithoutSummary(10_000);
        int refreshed = missing.stream().mapToInt(this::refresh).sum();
        if (refreshed > 0) {
            log.info("Computed metadata summaries for {} asset(s)", refreshed);
        }
    }

    @Scheduled(cron = "${dam.metadata.summary-refresh-cron:0 30 2 * * *}")
    void refreshAll() {
        int refreshed = 0;
        for (int offset = 0; ; offset += BATCH_SIZE) {
            List<Asset> page = assetRepository.findPage(null, offset, BATCH_SIZE);
            refreshed += page.stream().mapToInt(asset -> refresh(asset.id())).sum();
            if (page.size() < BATCH_SIZE) {
                break;
            }
        }
        log.info("Nightly metadata revalidation refreshed {} asset(s)", refreshed);
    }

    private int refresh(UUID assetId) {
        try {
            revalidationService.revalidate(assetId);
            return 1;
        } catch (RuntimeException e) {
            log.warn("Could not refresh metadata summary of asset {}: {}", assetId, e.toString());
            return 0;
        }
    }
}

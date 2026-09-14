package com.example.dam.search.application;

import com.example.dam.common.observability.MdcPropagatingExecutor;
import com.example.dam.metadata.application.MetadataChangedEvent;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Re-embeds assets after their metadata changed (after commit, asynchronously by default) and periodically
 * repairs missing or stale embeddings.
 */
@Component
class MetadataIndexingListener implements DisposableBean {

    private final AssetIndexingService indexingService;
    private final IndexingProperties properties;
    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
    private final MdcPropagatingExecutor executor = new MdcPropagatingExecutor(virtualThreads);

    MetadataIndexingListener(AssetIndexingService indexingService, IndexingProperties properties) {
        this.indexingService = indexingService;
        this.properties = properties;
    }

    @ConfigurationProperties("dam.indexing")
    record IndexingProperties(@DefaultValue("true") boolean async,
                              @DefaultValue("5m") Duration reindexInterval,
                              @DefaultValue("50") int reindexBatchSize) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onMetadataChanged(MetadataChangedEvent event) {
        if (properties.async()) {
            executor.execute(() -> indexingService.index(event.assetId()));
        } else {
            indexingService.index(event.assetId());
        }
    }

    @Scheduled(initialDelayString = "${dam.indexing.reindex-interval:5m}",
            fixedDelayString = "${dam.indexing.reindex-interval:5m}")
    void reindexStale() {
        indexingService.reindexStale(properties.reindexBatchSize());
    }

    @Override
    public void destroy() {
        virtualThreads.close();
    }
}

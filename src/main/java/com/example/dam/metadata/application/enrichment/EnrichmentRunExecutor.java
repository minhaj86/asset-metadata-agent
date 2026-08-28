package com.example.dam.metadata.application.enrichment;

import com.example.dam.common.observability.MdcPropagatingExecutor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Runs enrichment workflows in the background on virtual threads with bounded concurrency, so that bulk requests
 * queue up instead of overloading the model provider. The caller's MDC (correlation id) is propagated.
 */
@Component
class EnrichmentRunExecutor implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentRunExecutor.class);

    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
    private final MdcPropagatingExecutor executor = new MdcPropagatingExecutor(threads);
    private final Semaphore permits;

    EnrichmentRunExecutor(EnrichmentProperties properties) {
        this.permits = new Semaphore(properties.maxConcurrentRuns(), true);
    }

    void submit(Runnable run) {
        executor.execute(() -> {
            try {
                permits.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Enrichment run interrupted while queued");
                return;
            }
            try {
                run.run();
            } catch (RuntimeException e) {
                log.debug("Background enrichment run ended with {}", e.toString());
            } finally {
                permits.release();
            }
        });
    }

    @Override
    public void destroy() {
        threads.shutdownNow();
    }
}

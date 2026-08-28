package com.example.dam.metadata.application.enrichment;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Runs that were queued or executing when the application stopped can never finish; mark them as failed at
 * startup so that clients do not wait for them forever.
 */
@Component
class EnrichmentRunRecovery implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentRunRecovery.class);

    private final EnrichmentRunRepository repository;
    private final Clock clock;

    EnrichmentRunRecovery(EnrichmentRunRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        int interrupted = repository.failUnfinished("INTERRUPTED", "Interrupted by an application restart",
                clock.instant());
        if (interrupted > 0) {
            log.warn("Marked {} unfinished enrichment run(s) as failed after restart", interrupted);
        }
    }
}

package com.example.dam.metadata.application.enrichment;

import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for enrichment workflow runs. */
public interface EnrichmentRunRepository {

    void insert(EnrichmentRun run);

    void update(EnrichmentRun run);

    Optional<EnrichmentRun> findById(UUID id);

    /** Newest first; {@code null} filters are ignored. */
    List<EnrichmentRun> findRecent(UUID assetId, RunStatus status, int limit);

    /** Marks runs that never finished (e.g. interrupted by a restart) as failed; returns how many were affected. */
    int failUnfinished(String errorCode, String errorMessage, Instant now);
}

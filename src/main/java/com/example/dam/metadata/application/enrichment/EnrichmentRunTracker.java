package com.example.dam.metadata.application.enrichment;

import com.example.dam.asset.domain.Asset;
import com.example.dam.common.domain.Actor;
import com.example.dam.common.observability.CorrelationContext;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunStatus;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunSummary;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tracks enrichment runs: persists their lifecycle, keeps live per-stage progress of active runs in memory and
 * notifies subscribers (e.g. server-sent events) with full snapshots in order. Progress callbacks from the AI layer
 * never throw: tracking problems must not break a workflow.
 * <p>
 * Live progress is held by the instance executing the run; other instances see the persisted state (lifecycle and
 * final stages). A multi-instance deployment would publish progress through a shared channel instead.
 */
@Component
public class EnrichmentRunTracker implements EnrichmentProgressListener {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentRunTracker.class);

    private final EnrichmentRunRepository repository;
    private final Clock clock;
    private final Map<UUID, LiveRun> live = new ConcurrentHashMap<>();

    public EnrichmentRunTracker(EnrichmentRunRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Creates and persists a QUEUED run with all planned stages WAITING. */
    public EnrichmentRun create(Asset asset, Actor requestedBy, List<WorkflowStageDefinition> plan, String aiModel) {
        EnrichmentRun run = EnrichmentRun.create(UUID.randomUUID(), asset.id(), asset.externalAssetId(),
                requestedBy.id(), aiModel, CorrelationContext.correlationId().orElse(null), plan, clock.instant());
        repository.insert(run);
        live.put(run.id(), new LiveRun(run));
        return run;
    }

    public void markRunning(UUID runId) {
        change(runId, run -> run.running(clock.instant()), true);
    }

    @Override
    public void stageStarted(UUID workflowRunId, String stageId) {
        safely(() -> change(workflowRunId, run -> run.stageStarted(stageId, clock.instant()), false));
    }

    @Override
    public void stageFinished(UUID workflowRunId, AgentStepTrace step) {
        safely(() -> change(workflowRunId, run -> run.stageFinished(step), false));
    }

    /** The run's business outcome is already committed at this point; tracking failures are only logged. */
    public void complete(UUID runId, RunSummary summary, List<String> warnings) {
        safely(() -> change(runId, run -> run.completed(summary, warnings, clock.instant()), true));
    }

    public void fail(UUID runId, String errorCode, String errorMessage) {
        safely(() -> change(runId, run -> run.failed(errorCode, errorMessage, clock.instant()), true));
    }

    public Optional<EnrichmentRun> find(UUID runId) {
        LiveRun liveRun = live.get(runId);
        return liveRun != null ? Optional.of(liveRun.current()) : repository.findById(runId);
    }

    /** Persisted runs, newest first, with the live state of runs that are still executing on this instance. */
    public List<EnrichmentRun> recent(UUID assetId, RunStatus status, int limit) {
        List<EnrichmentRun> runs = new ArrayList<>();
        for (EnrichmentRun stored : repository.findRecent(assetId, status, limit)) {
            LiveRun liveRun = live.get(stored.id());
            runs.add(liveRun == null ? stored : liveRun.current());
        }
        return runs;
    }

    /**
     * Delivers the current snapshot immediately and every later change until the run is finished. The returned
     * subscription is empty when the run does not exist.
     */
    public Optional<Subscription> subscribe(UUID runId, Consumer<EnrichmentRun> subscriber) {
        LiveRun liveRun = live.get(runId);
        if (liveRun == null) {
            Optional<EnrichmentRun> stored = repository.findById(runId);
            stored.ifPresent(subscriber);
            return stored.map(run -> () -> {
            });
        }
        liveRun.subscribe(subscriber);
        return Optional.of(() -> liveRun.unsubscribe(subscriber));
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    private void change(UUID runId, UnaryOperator<EnrichmentRun> transition, boolean persist) {
        LiveRun liveRun = live.get(runId);
        if (liveRun == null) {
            if (persist) {
                repository.findById(runId).map(transition).ifPresent(repository::update);
            }
            return;
        }
        EnrichmentRun updated = liveRun.apply(transition, persist ? repository::update : null);
        if (updated.status().isTerminal()) {
            live.remove(runId);
        }
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Enrichment run tracking failed: {}", e.toString());
        }
    }

    /** Mutable holder of one active run; transitions and notifications are serialised to keep snapshots ordered. */
    private static final class LiveRun {

        private final List<Consumer<EnrichmentRun>> subscribers = new CopyOnWriteArrayList<>();
        private EnrichmentRun current;

        LiveRun(EnrichmentRun initial) {
            this.current = initial;
        }

        synchronized EnrichmentRun current() {
            return current;
        }

        synchronized EnrichmentRun apply(UnaryOperator<EnrichmentRun> transition, Consumer<EnrichmentRun> persister) {
            current = transition.apply(current);
            if (persister != null) {
                persister.accept(current);
            }
            for (Consumer<EnrichmentRun> subscriber : subscribers) {
                notify(subscriber, current);
            }
            return current;
        }

        synchronized void subscribe(Consumer<EnrichmentRun> subscriber) {
            if (notify(subscriber, current) && !current.status().isTerminal()) {
                subscribers.add(subscriber);
            }
        }

        void unsubscribe(Consumer<EnrichmentRun> subscriber) {
            subscribers.remove(subscriber);
        }

        private boolean notify(Consumer<EnrichmentRun> subscriber, EnrichmentRun snapshot) {
            try {
                subscriber.accept(snapshot);
                return true;
            } catch (RuntimeException e) {
                subscribers.remove(subscriber);
                return false;
            }
        }
    }
}

package com.example.dam.system.application;

import com.example.dam.asset.application.port.DamClient;
import com.example.dam.asset.application.port.DamClient.DamHealth;
import com.example.dam.metadata.application.ConfidenceThresholdProperties;
import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.application.enrichment.EnrichmentProperties;
import com.example.dam.search.application.VectorSearchStatusService;
import com.example.dam.search.application.VectorSearchStatusService.VectorSearchStatus;
import com.example.dam.system.application.port.AiStatusProvider;
import com.example.dam.system.application.port.AiStatusProvider.AiStatus;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Aggregated capability status for clients: which optional capabilities (AI, vector search, DAM) are usable and
 * which governance settings are in effect. Core metadata management never depends on these capabilities.
 */
@Service
public class SystemStatusService {

    private static final Logger log = LoggerFactory.getLogger(SystemStatusService.class);

    private final ObjectProvider<AiStatusProvider> aiStatusProvider;
    private final VectorSearchStatusService vectorSearchStatus;
    private final DamClient damClient;
    private final MetadataSchemaService schemaService;
    private final ConfidenceThresholdProperties thresholds;
    private final EnrichmentProperties enrichmentProperties;
    private final String environment;
    private final Clock clock;

    public SystemStatusService(ObjectProvider<AiStatusProvider> aiStatusProvider,
                               VectorSearchStatusService vectorSearchStatus, DamClient damClient,
                               MetadataSchemaService schemaService, ConfidenceThresholdProperties thresholds,
                               EnrichmentProperties enrichmentProperties,
                               @Value("${dam.environment:local}") String environment, Clock clock) {
        this.aiStatusProvider = aiStatusProvider;
        this.vectorSearchStatus = vectorSearchStatus;
        this.damClient = damClient;
        this.schemaService = schemaService;
        this.thresholds = thresholds;
        this.enrichmentProperties = enrichmentProperties;
        this.environment = environment;
        this.clock = clock;
    }

    public SystemStatus status() {
        return new SystemStatus(clock.instant(), environment, ai(), vectorSearch(), dam(),
                new Governance(thresholds.autoApprove(), thresholds.humanReview(), schemaService.definition().version()),
                enrichmentProperties.maxConcurrentRuns());
    }

    private CapabilityStatus ai() {
        AiStatusProvider provider = aiStatusProvider.getIfAvailable();
        if (provider == null) {
            return new CapabilityStatus(State.DISABLED, "AI layer disabled (ai.enabled=false); manual metadata "
                    + "management is fully available", null, null, null, null, null);
        }
        AiStatus status = provider.aiStatus();
        return new CapabilityStatus(status.available() ? State.AVAILABLE : State.UNAVAILABLE,
                status.available() ? null : status.unavailableReason(), status.provider(), status.chatModel(),
                status.embeddingModel(), status.workflowTimeout() == null ? null : status.workflowTimeout().toSeconds(),
                status.maxToolInvocations());
    }

    private CapabilityStatus vectorSearch() {
        VectorSearchStatus status = vectorSearchStatus.status();
        State state = switch (status.state()) {
            case AVAILABLE -> State.AVAILABLE;
            case UNAVAILABLE -> State.UNAVAILABLE;
            case DISABLED -> State.DISABLED;
        };
        return new CapabilityStatus(state, status.detail(), null, null, status.embeddingModel(), null, null);
    }

    private CapabilityStatus dam() {
        try {
            DamHealth health = damClient.health();
            return new CapabilityStatus(health.available() ? State.AVAILABLE : State.UNAVAILABLE, health.detail(),
                    health.system(), null, null, null, null);
        } catch (RuntimeException e) {
            log.warn("DAM health check failed: {}", e.toString());
            return new CapabilityStatus(State.UNAVAILABLE, "DAM not reachable", null, null, null, null, null);
        }
    }

    public enum State { AVAILABLE, UNAVAILABLE, DISABLED }

    /**
     * Status of one optional capability; attributes that do not apply are {@code null}.
     *
     * @param provider vendor or system behind the capability
     */
    public record CapabilityStatus(State state, String detail, String provider, String model, String embeddingModel,
                                   Long timeoutSeconds, Integer maxToolInvocations) {
    }

    public record Governance(double autoApproveThreshold, double humanReviewThreshold, int schemaVersion) {
    }

    public record SystemStatus(Instant checkedAt, String environment, CapabilityStatus ai,
                               CapabilityStatus vectorSearch, CapabilityStatus dam, Governance governance,
                               int maxConcurrentEnrichments) {
    }
}

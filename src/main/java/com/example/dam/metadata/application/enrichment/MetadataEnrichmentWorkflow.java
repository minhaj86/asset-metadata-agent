package com.example.dam.metadata.application.enrichment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port to the AI-driven enrichment workflow. Implemented with LangChain4j Agentic in the AI layer; the
 * application depends only on this interface, which shields it from the (experimental) framework API.
 * Implementations must not modify metadata: they return proposals only.
 */
public interface MetadataEnrichmentWorkflow {

    /**
     * Runs the workflow. Stage progress is reported to {@link EnrichmentProgressListener} under the given run id.
     */
    MetadataEnrichmentResult enrich(UUID assetId, UUID workflowRunId);

    default MetadataEnrichmentResult enrich(UUID assetId) {
        return enrich(assetId, UUID.randomUUID());
    }

    /** Planned stages in execution order. */
    List<WorkflowStageDefinition> stages();

    /** Provenance label of the model the workflow uses, e.g. {@code openai:gpt-4.1-mini}. */
    String aiModel();

    /** Present when the workflow cannot run (e.g. the model provider is misconfigured). */
    default Optional<String> unavailableReason() {
        return Optional.empty();
    }
}

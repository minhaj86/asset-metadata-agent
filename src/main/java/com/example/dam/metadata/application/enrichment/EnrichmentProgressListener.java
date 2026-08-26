package com.example.dam.metadata.application.enrichment;

import java.util.UUID;

/**
 * Receives stage progress of running enrichment workflows. Implemented by the application (run tracking) and
 * called by the AI layer; implementations must be fast and must never throw into the workflow.
 */
public interface EnrichmentProgressListener {

    void stageStarted(UUID workflowRunId, String stageId);

    void stageFinished(UUID workflowRunId, AgentStepTrace step);
}

package com.example.dam.metadata.application.enrichment;

import com.example.dam.metadata.application.enrichment.AgentStepTrace.StepKind;
import java.util.Objects;

/**
 * One stage of the enrichment workflow as published by the workflow implementation, so that clients can show the
 * planned topology before and while it runs.
 *
 * @param id            stable stage id (the agent or step name reported in progress events)
 * @param parallelGroup stages sharing a group run concurrently; {@code null} for sequential stages
 * @param conditional   the stage only runs when its activation condition holds
 */
public record WorkflowStageDefinition(String id, String displayName, StepKind kind, String description,
                                      String parallelGroup, boolean conditional) {

    public WorkflowStageDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(kind, "kind");
        description = description == null ? "" : description;
    }
}

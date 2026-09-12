package com.example.dam.ai.workflow.step;

import com.example.dam.ai.workflow.ApprovalPlan;
import com.example.dam.ai.workflow.EnrichmentState;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import java.util.List;

/**
 * Conditional branch "Auto-approved": selected only when the plan contains auto-approvable proposals. It prepares
 * the change set note; the application layer re-checks every decision and applies the changes transactionally.
 */
public class AutoApprovalRoutingStep {

    @Agent(name = "autoApprovalRouting", description = "Prepares automatically applicable changes",
            outputKey = EnrichmentState.AUTO_APPROVAL_NOTE)
    public String route(@K(EnrichmentState.Plan.class) ApprovalPlan plan) {
        List<String> fields = plan.fields(ApprovalRoute.AUTO_APPROVE);
        return "%d field(s) eligible for automatic application after re-validation: %s".formatted(fields.size(), fields);
    }
}

package com.example.dam.ai.workflow.step;

import com.example.dam.ai.workflow.ApprovalPlan;
import com.example.dam.ai.workflow.EnrichmentState;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import java.util.List;

/**
 * Conditional branch "Human approval": selected only when proposals need a human decision. Approval requests are
 * persisted (durable, asynchronous human-in-the-loop) rather than suspending the in-memory agentic system.
 */
public class HumanReviewRoutingStep {

    @Agent(name = "humanReviewRouting", description = "Prepares approval requests for a human reviewer",
            outputKey = EnrichmentState.HUMAN_REVIEW_NOTE)
    public String route(@K(EnrichmentState.Plan.class) ApprovalPlan plan) {
        List<String> review = plan.fields(ApprovalRoute.HUMAN_REVIEW);
        List<String> suggestions = plan.fields(ApprovalRoute.LOW_CONFIDENCE);
        return "%d approval request(s) %s and %d low-confidence suggestion(s) %s".formatted(review.size(), review,
                suggestions.size(), suggestions);
    }
}

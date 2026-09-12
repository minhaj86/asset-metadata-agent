package com.example.dam.ai.workflow.step;

import com.example.dam.ai.workflow.ApprovalPlan;
import com.example.dam.ai.workflow.CandidateSet;
import com.example.dam.ai.workflow.EnrichmentState;
import com.example.dam.metadata.application.enrichment.AssetEnrichmentContext;
import com.example.dam.metadata.application.enrichment.PlannedProposal;
import com.example.dam.metadata.domain.proposal.ApprovalDecision;
import com.example.dam.metadata.domain.proposal.ApprovalPolicy;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import java.util.List;

/**
 * Deterministic step "Approval decision": applies the domain {@link ApprovalPolicy} (confidence thresholds plus
 * schema safety rules) to every candidate. No LLM is involved in this decision.
 */
public class ApprovalDecisionStep {

    private final ApprovalPolicy approvalPolicy;

    public ApprovalDecisionStep(ApprovalPolicy approvalPolicy) {
        this.approvalPolicy = approvalPolicy;
    }

    @Agent(name = "approvalDecision", description = "Routes candidates by deterministic approval policy",
            typedOutputKey = EnrichmentState.Plan.class)
    public ApprovalPlan decide(@K(EnrichmentState.Context.class) AssetEnrichmentContext context,
                               @K(EnrichmentState.Candidates.class) CandidateSet candidates) {
        List<PlannedProposal> planned = candidates.candidates().stream().map(candidate -> {
            ApprovalDecision decision = approvalPolicy.decide(candidate.fieldKey(),
                    context.schema().field(candidate.fieldKey()),
                    context.currentMetadata().entry(candidate.fieldKey()), candidate.value(), candidate.confidence());
            return new PlannedProposal(candidate, decision.route(), decision.reason());
        }).toList();
        return new ApprovalPlan(planned);
    }
}

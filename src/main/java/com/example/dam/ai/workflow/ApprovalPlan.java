package com.example.dam.ai.workflow;

import com.example.dam.ai.observability.StepOutcome;
import com.example.dam.metadata.application.enrichment.PlannedProposal;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Deterministic routing plan computed inside the workflow (re-checked when persisted). */
public record ApprovalPlan(List<PlannedProposal> proposals) implements StepOutcome {

    public ApprovalPlan {
        proposals = List.copyOf(proposals);
    }

    public boolean has(ApprovalRoute... routes) {
        List<ApprovalRoute> wanted = Arrays.asList(routes);
        return proposals.stream().anyMatch(proposal -> wanted.contains(proposal.plannedRoute()));
    }

    public List<String> fields(ApprovalRoute route) {
        return proposals.stream()
                .filter(proposal -> proposal.plannedRoute() == route)
                .map(proposal -> proposal.candidate().fieldKey())
                .toList();
    }

    @Override
    public String outcomeSummary() {
        Map<ApprovalRoute, Long> counts = proposals.stream()
                .collect(Collectors.groupingBy(PlannedProposal::plannedRoute, Collectors.counting()));
        return "planned routes " + counts;
    }
}

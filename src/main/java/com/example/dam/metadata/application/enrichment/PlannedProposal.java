package com.example.dam.metadata.application.enrichment;

import com.example.dam.metadata.domain.proposal.ApprovalRoute;

/**
 * Candidate plus the route planned inside the workflow. The route is re-evaluated against the latest state when
 * the result is persisted; the persisted decision is authoritative.
 */
public record PlannedProposal(MetadataCandidate candidate, ApprovalRoute plannedRoute, String plannedReason) {
}

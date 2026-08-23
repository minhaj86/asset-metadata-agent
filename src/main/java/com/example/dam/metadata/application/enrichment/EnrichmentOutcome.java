package com.example.dam.metadata.application.enrichment;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.metadata.application.quality.SemanticQualityAnalyzer.SemanticQualityAssessment;
import com.example.dam.metadata.domain.MetadataStatus;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import com.example.dam.metadata.domain.value.MetadataValue;
import java.util.List;
import java.util.UUID;

/** What an enrichment run did after deterministic policy evaluation and persistence. */
public record EnrichmentOutcome(
        UUID workflowRunId,
        UUID assetId,
        AssetStatus assetStatus,
        List<ProposalOutcome> proposals,
        TaxonomySuggestion taxonomy,
        SemanticQualityAssessment qualityReview,
        List<AgentStepTrace> steps,
        List<String> warnings,
        String aiModel) {

    public EnrichmentOutcome {
        proposals = List.copyOf(proposals);
        steps = List.copyOf(steps);
        warnings = List.copyOf(warnings);
    }

    /**
     * @param proposalId {@code null} for NO_CHANGE outcomes, which are not persisted
     */
    public record ProposalOutcome(UUID proposalId, String fieldKey, MetadataValue value, double confidence,
                                  ApprovalRoute route, MetadataStatus status, String reason, String explanation,
                                  List<String> evidence, String proposedBy) {
        public ProposalOutcome {
            evidence = List.copyOf(evidence);
        }
    }
}

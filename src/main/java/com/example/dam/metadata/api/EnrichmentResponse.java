package com.example.dam.metadata.api;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.metadata.application.enrichment.AgentStepTrace;
import com.example.dam.metadata.application.enrichment.EnrichmentOutcome;
import com.example.dam.metadata.application.enrichment.EnrichmentOutcome.ProposalOutcome;
import com.example.dam.metadata.application.enrichment.TaxonomySuggestion;
import com.example.dam.metadata.application.quality.SemanticQualityAnalyzer.SemanticQualityAssessment;
import com.example.dam.metadata.domain.MetadataStatus;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import java.util.List;
import java.util.UUID;

public record EnrichmentResponse(
        UUID workflowRunId,
        UUID assetId,
        AssetStatus assetStatus,
        Summary summary,
        List<ProposalOutcomeResponse> proposals,
        TaxonomySuggestion taxonomy,
        QualityReviewResponse qualityReview,
        List<AgentStepTrace> agentSteps,
        List<String> warnings,
        String aiModel) {

    public record Summary(long autoApplied, long pendingReview, long lowConfidenceSuggestions, long rejectedByPolicy,
                          long unchanged) {
    }

    public record ProposalOutcomeResponse(UUID proposalId, String field, Object value, double confidence,
                                          ApprovalRoute route, MetadataStatus status, String reason,
                                          String explanation, List<String> evidence, String proposedBy) {
        static ProposalOutcomeResponse from(ProposalOutcome outcome) {
            return new ProposalOutcomeResponse(outcome.proposalId(), outcome.fieldKey(),
                    outcome.value() == null ? null : outcome.value().toPlainValue(), outcome.confidence(),
                    outcome.route(), outcome.status(), outcome.reason(), outcome.explanation(), outcome.evidence(),
                    outcome.proposedBy());
        }
    }

    public record QualityReviewResponse(List<QualityReportResponse.AiSuggestionResponse> suggestions,
                                        double confidence, String summary, String model) {
        static QualityReviewResponse from(SemanticQualityAssessment assessment) {
            return assessment == null ? null : new QualityReviewResponse(
                    assessment.suggestions().stream().map(QualityReportResponse.AiSuggestionResponse::from).toList(),
                    assessment.confidence(), assessment.summary(), assessment.model());
        }
    }

    static EnrichmentResponse from(EnrichmentOutcome outcome) {
        List<ProposalOutcome> proposals = outcome.proposals();
        Summary summary = new Summary(
                count(proposals, ApprovalRoute.AUTO_APPROVE),
                count(proposals, ApprovalRoute.HUMAN_REVIEW),
                count(proposals, ApprovalRoute.LOW_CONFIDENCE),
                count(proposals, ApprovalRoute.REJECTED_BY_POLICY),
                count(proposals, ApprovalRoute.NO_CHANGE));
        return new EnrichmentResponse(outcome.workflowRunId(), outcome.assetId(), outcome.assetStatus(), summary,
                proposals.stream().map(ProposalOutcomeResponse::from).toList(), outcome.taxonomy(),
                QualityReviewResponse.from(outcome.qualityReview()), outcome.steps(), outcome.warnings(),
                outcome.aiModel());
    }

    private static long count(List<ProposalOutcome> proposals, ApprovalRoute route) {
        return proposals.stream().filter(proposal -> proposal.route() == route).count();
    }
}

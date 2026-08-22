package com.example.dam.metadata.api;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.review.ProposalView;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.MetadataStatus;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import com.example.dam.metadata.domain.proposal.MetadataProposal;
import com.example.dam.metadata.domain.proposal.ProposalReviewPolicy.ReviewGuidance;
import com.example.dam.metadata.domain.proposal.ProposalReviewPolicy.RiskLevel;
import com.example.dam.metadata.domain.schema.MetadataDataType;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProposalResponse(
        UUID id,
        UUID assetId,
        String externalAssetId,
        AssetType assetType,
        String assetFilename,
        String field,
        FieldInfo fieldInfo,
        Object proposedValue,
        Object currentValue,
        @Schema(description = "Value applied on approval when the reviewer edited the proposal") Object appliedValue,
        double confidence,
        String explanation,
        List<String> evidence,
        MetadataSource source,
        @Schema(description = "Agent that produced the proposal") String proposedBy,
        String aiModel,
        UUID workflowRunId,
        ApprovalRoute route,
        String routeReason,
        MetadataStatus status,
        Instant createdAt,
        Instant decidedAt,
        String decidedBy,
        String decisionComment,
        @Schema(description = "Deterministic review guidance computed by the backend; clients only render it")
        ReviewPolicyResponse policy) {

    /**
     * @param definedInSchema {@code false} if the field was removed from the schema after the proposal was made
     */
    public record FieldInfo(boolean definedInSchema, String displayName, MetadataDataType dataType, String group,
                            boolean repeatable, List<String> allowedValues) {
        static FieldInfo from(String key, MetadataFieldDefinition field) {
            return field == null ? new FieldInfo(false, key, null, null, false, List.of())
                    : new FieldInfo(true, field.displayName(), field.dataType(), field.group(), field.repeatable(),
                    field.allowedValues());
        }
    }

    public record ReviewPolicyResponse(
            @Schema(description = "The schema requires a human decision for AI values of this field")
            boolean requiresHumanApproval,
            @Schema(description = "The schema allows AI values of this field to be applied automatically")
            boolean canAutoApprove,
            @Schema(description = "May be approved together with other proposals in one bulk action")
            boolean bulkApprovable,
            RiskLevel riskLevel,
            List<String> riskFactors) {
        static ReviewPolicyResponse from(ReviewGuidance guidance) {
            return new ReviewPolicyResponse(guidance.requiresHumanApproval(), guidance.canAutoApprove(),
                    guidance.bulkApprovable(), guidance.riskLevel(), guidance.riskFactors());
        }
    }

    static ProposalResponse from(ProposalView view) {
        MetadataProposal proposal = view.proposal();
        return new ProposalResponse(proposal.id(), proposal.assetId(), view.asset().externalAssetId(),
                view.asset().type(), view.asset().filename(), proposal.fieldKey(),
                FieldInfo.from(proposal.fieldKey(), view.field()),
                proposal.proposedValue().toPlainValue(),
                proposal.currentValue() == null ? null : proposal.currentValue().toPlainValue(),
                proposal.appliedValue() == null ? null : proposal.appliedValue().toPlainValue(),
                proposal.confidence(), proposal.explanation(), proposal.evidence(), proposal.source(),
                proposal.proposedBy(), proposal.aiModel(), proposal.workflowRunId(), proposal.route(),
                proposal.routeReason(), proposal.status(), proposal.createdAt(), proposal.decidedAt(),
                proposal.decidedBy(), proposal.decisionComment(), ReviewPolicyResponse.from(view.guidance()));
    }
}

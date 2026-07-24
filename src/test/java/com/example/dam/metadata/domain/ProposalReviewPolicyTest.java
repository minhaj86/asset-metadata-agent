package com.example.dam.metadata.domain;

import static com.example.dam.support.MetadataFixtures.NOW;
import static com.example.dam.support.MetadataFixtures.field;
import static com.example.dam.support.MetadataFixtures.value;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.common.domain.Actor;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import com.example.dam.metadata.domain.proposal.ConfidenceThresholds;
import com.example.dam.metadata.domain.proposal.MetadataProposal;
import com.example.dam.metadata.domain.proposal.ProposalReviewPolicy;
import com.example.dam.metadata.domain.proposal.ProposalReviewPolicy.ReviewGuidance;
import com.example.dam.metadata.domain.proposal.ProposalReviewPolicy.RiskLevel;
import com.example.dam.metadata.domain.value.MetadataValue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProposalReviewPolicyTest {

    private final ProposalReviewPolicy policy = new ProposalReviewPolicy(new ConfidenceThresholds(0.92, 0.70));

    @Test
    void sensitiveFieldsAreHighRiskAndNeverBulkApprovable() {
        ReviewGuidance guidance = policy.evaluate(proposal("taxonomyPath", "Fashion > Men > Clothing > Shirts", null, 0.99),
                Optional.of(field("taxonomyPath")));

        assertThat(guidance.requiresHumanApproval()).isTrue();
        assertThat(guidance.canAutoApprove()).isFalse();
        assertThat(guidance.bulkApprovable()).isFalse();
        assertThat(guidance.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(guidance.riskFactors()).anyMatch(factor -> factor.startsWith("Sensitive field"));
    }

    @Test
    void safeAutomatableFieldWithoutExistingValueIsBulkApprovable() {
        ReviewGuidance guidance = policy.evaluate(proposal("category", "TOPS", null, 0.85), Optional.of(field("category")));

        assertThat(guidance.canAutoApprove()).isTrue();
        assertThat(guidance.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(guidance.bulkApprovable()).isTrue();
    }

    @Test
    void replacingAValueOrLowConfidenceRaisesTheRiskAndRequiresIndividualReview() {
        ReviewGuidance replacing = policy.evaluate(proposal("category", "TOPS", value("category", "BOTTOMS"), 0.85),
                Optional.of(field("category")));
        ReviewGuidance uncertain = policy.evaluate(proposal("category", "TOPS", null, 0.55), Optional.of(field("category")));

        assertThat(replacing.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(replacing.bulkApprovable()).isFalse();
        assertThat(uncertain.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(uncertain.riskFactors()).contains("Confidence below the review threshold");
    }

    @Test
    void fieldsTheSchemaDoesNotAutomateAreReviewedOneByOne() {
        ReviewGuidance guidance = policy.evaluate(proposal("altText", "Front view of a shirt", null, 0.95),
                Optional.of(field("altText")));

        assertThat(guidance.requiresHumanApproval()).isFalse();
        assertThat(guidance.canAutoApprove()).isFalse();
        assertThat(guidance.bulkApprovable()).isFalse();
    }

    @Test
    void decidedProposalsAndRemovedFieldsAreNeverBulkApprovable() {
        MetadataProposal decided = proposal("category", "TOPS", null, 0.85).approve(Actor.human("reviewer"), null, NOW);

        assertThat(policy.evaluate(decided, Optional.of(field("category"))).bulkApprovable()).isFalse();
        ReviewGuidance removed = policy.evaluate(proposal("category", "TOPS", null, 0.85), Optional.empty());
        assertThat(removed.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(removed.requiresHumanApproval()).isTrue();
    }

    @Test
    void approvalWithAnEditedValueKeepsTheProposalAndRecordsTheAppliedValue() {
        MetadataProposal proposal = proposal("category", "TOPS", null, 0.85);

        MetadataProposal edited = proposal.approve(Actor.human("reviewer"), "fix", NOW, value("category", "OUTERWEAR"));
        MetadataProposal unchanged = proposal.approve(Actor.human("reviewer"), null, NOW, value("category", "tops"));

        assertThat(edited.wasEditedByReviewer()).isTrue();
        assertThat(edited.effectiveValue()).isEqualTo(value("category", "OUTERWEAR"));
        assertThat(edited.proposedValue()).isEqualTo(value("category", "TOPS"));
        assertThat(unchanged.wasEditedByReviewer()).isFalse();
    }

    private static MetadataProposal proposal(String field, Object proposed, MetadataValue current, double confidence) {
        return new MetadataProposal(UUID.randomUUID(), UUID.randomUUID(), field, value(field, proposed), current,
                confidence, "explanation", List.of("evidence"), MetadataSource.AI_GENERATED, "metadataEnrichmentAgent",
                "demo:model", UUID.randomUUID(), ApprovalRoute.HUMAN_REVIEW, "review", MetadataStatus.PROPOSED, NOW,
                null, null, null, null);
    }
}

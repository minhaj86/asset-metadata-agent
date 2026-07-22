package com.example.dam.metadata.domain;

import static com.example.dam.support.MetadataFixtures.aiValidated;
import static com.example.dam.support.MetadataFixtures.approved;
import static com.example.dam.support.MetadataFixtures.field;
import static com.example.dam.support.MetadataFixtures.value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.dam.metadata.domain.proposal.ApprovalDecision;
import com.example.dam.metadata.domain.proposal.ApprovalPolicy;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import com.example.dam.metadata.domain.proposal.ConfidenceThresholds;
import com.example.dam.metadata.domain.proposal.MetadataMergePolicy;
import com.example.dam.metadata.domain.validation.MetadataValueValidator;
import com.example.dam.metadata.domain.value.ListValue;
import com.example.dam.metadata.domain.value.TextValue;
import com.example.dam.support.MetadataFixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ApprovalPolicyTest {

    private final ApprovalPolicy policy = new ApprovalPolicy(new ConfidenceThresholds(0.92, 0.70),
            new MetadataMergePolicy(), new MetadataValueValidator(MetadataFixtures.REFERENCE_DATA));

    @Nested
    class ConfidenceThresholdRules {

        @Test
        void highConfidenceSafeFieldIsAutoApproved() {
            assertThat(decide("keywords", List.of("shirt", "oxford"), 0.95, null).route())
                    .isEqualTo(ApprovalRoute.AUTO_APPROVE);
        }

        @Test
        void exactlyTheAutoApprovalThresholdIsEnough() {
            assertThat(decide("color", "BLUE", 0.92, null).route()).isEqualTo(ApprovalRoute.AUTO_APPROVE);
        }

        @Test
        void mediumConfidenceGoesToHumanReview() {
            ApprovalDecision decision = decide("color", "BLUE", 0.85, null);

            assertThat(decision.route()).isEqualTo(ApprovalRoute.HUMAN_REVIEW);
            assertThat(decision.reason()).contains("below auto-approval threshold");
        }

        @Test
        void lowConfidenceIsKeptAsSuggestionOnly() {
            assertThat(decide("color", "BLUE", 0.55, null).route()).isEqualTo(ApprovalRoute.LOW_CONFIDENCE);
            assertThat(decide("copyrightOwner", "Sample Apparel", 0.55, null).route()).isEqualTo(ApprovalRoute.LOW_CONFIDENCE);
        }

        @Test
        void thresholdsMustBeConsistent() {
            assertThatThrownBy(() -> new ConfidenceThresholds(0.6, 0.8)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class SchemaSafetyRules {

        @Test
        void sensitiveFieldRequiresHumanApprovalRegardlessOfConfidence() {
            ApprovalDecision angle = decide("angleFacing", "FRONT", 0.99, null);
            ApprovalDecision copyright = decide("copyrightOwner", "Sample Apparel", 0.99, null);
            ApprovalDecision taxonomy = decide("taxonomyPath", "Fashion > Men > Clothing > Shirts", 0.99, null);

            assertThat(List.of(angle.route(), copyright.route(), taxonomy.route())).containsOnly(ApprovalRoute.HUMAN_REVIEW);
            assertThat(angle.reason()).contains("Schema requires human approval");
        }

        @Test
        void fieldWithoutAutoApplyPermissionGoesToReview() {
            ApprovalDecision decision = decide("altText", "Front view of a blue shirt on a white background", 0.99, null);

            assertThat(decision.route()).isEqualTo(ApprovalRoute.HUMAN_REVIEW);
            assertThat(decision.reason()).contains("does not permit automatic updates");
        }

        @Test
        void nonEnrichableFieldIsRejected() {
            assertThat(decide("usageRightsEnd", "2030-01-01", 0.99, null).route()).isEqualTo(ApprovalRoute.REJECTED_BY_POLICY);
            assertThat(decide("productId", "99999", 0.99, null).route()).isEqualTo(ApprovalRoute.REJECTED_BY_POLICY);
        }

        @Test
        void unknownFieldIsRejected() {
            ApprovalDecision decision = policy.decide("mood", Optional.empty(), Optional.empty(), new TextValue("happy"), 0.99);

            assertThat(decision.route()).isEqualTo(ApprovalRoute.REJECTED_BY_POLICY);
        }

        @Test
        void inventedTaxonomyPathIsRejectedByValidation() {
            ApprovalDecision decision = decide("taxonomyPath", "Fashion > Men > Clothing > Space Suits", 0.99, null);

            assertThat(decision.route()).isEqualTo(ApprovalRoute.REJECTED_BY_POLICY);
            assertThat(decision.reason()).contains("Unknown taxonomy path");
        }

        @Test
        void valueOutsideVocabularyIsRejected() {
            ApprovalDecision decision = policy.decide("color", Optional.of(field("color")), Optional.empty(),
                    new TextValue("TURQUOISE"), 0.99);

            assertThat(decision.route()).isEqualTo(ApprovalRoute.REJECTED_BY_POLICY);
        }
    }

    @Nested
    class ExistingMetadataRules {

        @Test
        void authoritativeValueIsNeverOverwritten() {
            ApprovalDecision decision = decide("color", "BLUE", 0.99, approved("color", "RED"));

            assertThat(decision.route()).isEqualTo(ApprovalRoute.REJECTED_BY_POLICY);
            assertThat(decision.reason()).contains("authoritative");
        }

        @Test
        void replacingMachineGeneratedValueNeedsHumanReview() {
            ApprovalDecision decision = decide("color", "BLUE", 0.99, aiValidated("color", "RED"));

            assertThat(decision.route()).isEqualTo(ApprovalRoute.HUMAN_REVIEW);
            assertThat(decision.reason()).contains("replace");
        }

        @Test
        void equivalentValueIsNoChange() {
            assertThat(decide("color", "blue", 0.99, aiValidated("color", "BLUE")).route()).isEqualTo(ApprovalRoute.NO_CHANGE);
            assertThat(decide("color", "BLUE", 0.99, approved("color", "BLUE")).isRecordable()).isFalse();
        }

        @Test
        void extendingHumanApprovedListNeedsReviewAndKeepsExistingItems() {
            ApprovalDecision decision = decide("keywords", List.of("oxford"), 0.99, approved("keywords", List.of("shirt")));

            assertThat(decision.route()).isEqualTo(ApprovalRoute.HUMAN_REVIEW);
            assertThat(decision.valueToApply()).isEqualTo(ListValue.ofTexts(List.of("shirt", "oxford")));
        }

        @Test
        void extendingMachineGeneratedListMayBeAutoApplied() {
            ApprovalDecision decision = decide("keywords", List.of("oxford"), 0.95, aiValidated("keywords", List.of("shirt")));

            assertThat(decision.route()).isEqualTo(ApprovalRoute.AUTO_APPROVE);
            assertThat(decision.valueToApply()).isEqualTo(ListValue.ofTexts(List.of("shirt", "oxford")));
        }
    }

    private ApprovalDecision decide(String key, Object raw, double confidence, AssetMetadata existing) {
        return policy.decide(key, Optional.of(field(key)), Optional.ofNullable(existing), value(key, raw), confidence);
    }
}

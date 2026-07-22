package com.example.dam.metadata.domain.proposal;

import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.proposal.MetadataMergePolicy.MergeKind;
import com.example.dam.metadata.domain.proposal.MetadataMergePolicy.MergeResult;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.validation.MetadataValueValidator;
import com.example.dam.metadata.domain.validation.ValidationIssue;
import com.example.dam.metadata.domain.value.MetadataValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Decides deterministically what happens with a machine-generated proposal. Evaluation order matters:
 * <ol>
 *   <li>Governance: unsupported or non-AI-enrichable fields are rejected.</li>
 *   <li>Validation: values violating the schema (incl. unknown taxonomy paths) are rejected.</li>
 *   <li>No-op detection: equivalent values are not recorded.</li>
 *   <li>Authority: AI never replaces authoritative (APPROVED) values.</li>
 *   <li>Confidence floor: below the review threshold the proposal is only a suggestion.</li>
 *   <li>Schema safety: fields requiring human approval always go to review, whatever the confidence.</li>
 *   <li>Overwrites of existing values and changes to human-approved lists go to review.</li>
 *   <li>Auto-approval only when confidence reaches the threshold AND the schema permits automatic updates.</li>
 * </ol>
 */
public final class ApprovalPolicy {

    private final ConfidenceThresholds thresholds;
    private final MetadataMergePolicy mergePolicy;
    private final MetadataValueValidator valueValidator;

    public ApprovalPolicy(ConfidenceThresholds thresholds, MetadataMergePolicy mergePolicy,
                          MetadataValueValidator valueValidator) {
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds");
        this.mergePolicy = Objects.requireNonNull(mergePolicy, "mergePolicy");
        this.valueValidator = Objects.requireNonNull(valueValidator, "valueValidator");
    }

    public ConfidenceThresholds thresholds() {
        return thresholds;
    }

    public ApprovalDecision decide(String fieldKey, Optional<MetadataFieldDefinition> definition,
                                   Optional<AssetMetadata> existing, MetadataValue proposed, double confidence) {
        if (definition.isEmpty()) {
            return rejected("Field '%s' is not defined in the metadata schema".formatted(fieldKey), proposed);
        }
        MetadataFieldDefinition field = definition.get();
        if (!field.aiPolicy().enrichable()) {
            return rejected("Schema policy forbids AI-generated values for '%s'".formatted(fieldKey), proposed);
        }
        List<ValidationIssue> errors = valueValidator.validate(field, proposed).stream()
                .filter(ValidationIssue::isError)
                .toList();
        if (!errors.isEmpty()) {
            return rejected("Proposed value failed validation: " + errors.stream()
                    .map(ValidationIssue::message).collect(Collectors.joining("; ")), proposed);
        }

        MergeResult merge = mergePolicy.merge(field, existing, proposed);
        if (merge.kind() == MergeKind.UNCHANGED) {
            return new ApprovalDecision(ApprovalRoute.NO_CHANGE, "Proposed value equals current value",
                    merge.resultingValue());
        }
        boolean authoritative = existing.map(AssetMetadata::isAuthoritative).orElse(false);
        if (authoritative && merge.kind() == MergeKind.REPLACEMENT) {
            return rejected("Would overwrite an authoritative value (%s)".formatted(existing.get().source()),
                    merge.resultingValue());
        }
        if (confidence < thresholds.humanReview()) {
            return new ApprovalDecision(ApprovalRoute.LOW_CONFIDENCE,
                    "Confidence %.2f below review threshold %.2f".formatted(confidence, thresholds.humanReview()),
                    merge.resultingValue());
        }
        if (field.aiPolicy().humanApprovalRequired()) {
            return review("Schema requires human approval for '%s'".formatted(fieldKey), merge);
        }
        if (merge.kind() == MergeKind.REPLACEMENT) {
            return review("Would replace the existing value", merge);
        }
        if (authoritative) {
            return review("Would extend a human-approved value", merge);
        }
        if (!field.aiPolicy().autoApply()) {
            return review("Schema does not permit automatic updates of '%s'".formatted(fieldKey), merge);
        }
        if (confidence >= thresholds.autoApprove()) {
            return new ApprovalDecision(ApprovalRoute.AUTO_APPROVE,
                    "Confidence %.2f >= %.2f and schema permits automatic updates"
                            .formatted(confidence, thresholds.autoApprove()),
                    merge.resultingValue());
        }
        return review("Confidence %.2f below auto-approval threshold %.2f"
                .formatted(confidence, thresholds.autoApprove()), merge);
    }

    private static ApprovalDecision rejected(String reason, MetadataValue value) {
        return new ApprovalDecision(ApprovalRoute.REJECTED_BY_POLICY, reason, value);
    }

    private static ApprovalDecision review(String reason, MergeResult merge) {
        return new ApprovalDecision(ApprovalRoute.HUMAN_REVIEW, reason, merge.resultingValue());
    }
}

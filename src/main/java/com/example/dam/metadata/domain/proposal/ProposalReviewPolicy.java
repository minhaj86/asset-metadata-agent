package com.example.dam.metadata.domain.proposal;

import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Derives deterministic review guidance for a proposal from schema governance and the proposal itself. User
 * interfaces render this guidance; they never decide whether a field is safe themselves.
 * <ul>
 *   <li>{@code requiresHumanApproval}: the schema requires a human decision for every AI value of the field.</li>
 *   <li>{@code canAutoApprove}: the schema allows AI values of the field to be applied automatically (subject to
 *       the confidence threshold).</li>
 *   <li>{@code riskLevel}: HIGH for schema-sensitive fields, MEDIUM when an existing value would be replaced or
 *       the confidence is below the review threshold, LOW otherwise.</li>
 *   <li>{@code bulkApprovable}: open, auto-approvable field and LOW risk. Everything else is reviewed one by one.</li>
 * </ul>
 */
public final class ProposalReviewPolicy {

    private final ConfidenceThresholds thresholds;

    public ProposalReviewPolicy(ConfidenceThresholds thresholds) {
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds");
    }

    public ReviewGuidance evaluate(MetadataProposal proposal, Optional<MetadataFieldDefinition> field) {
        if (field.isEmpty()) {
            return new ReviewGuidance(true, false, false, RiskLevel.HIGH,
                    List.of("Field is no longer defined in the metadata schema"));
        }
        MetadataFieldDefinition definition = field.get();
        boolean requiresHumanApproval = definition.aiPolicy().humanApprovalRequired();
        boolean canAutoApprove = definition.aiPolicy().enrichable() && definition.aiPolicy().autoApply()
                && !requiresHumanApproval;
        List<String> factors = new ArrayList<>();
        if (requiresHumanApproval) {
            factors.add("Sensitive field: the schema requires human approval");
        }
        if (!definition.aiPolicy().enrichable()) {
            factors.add("The schema does not allow AI values for this field");
        }
        if (proposal.currentValue() != null) {
            factors.add("Replaces an existing value");
        }
        if (proposal.confidence() < thresholds.humanReview()) {
            factors.add("Confidence below the review threshold");
        }
        RiskLevel risk = requiresHumanApproval || !definition.aiPolicy().enrichable() ? RiskLevel.HIGH
                : factors.isEmpty() ? RiskLevel.LOW : RiskLevel.MEDIUM;
        boolean bulkApprovable = proposal.isOpen() && canAutoApprove && risk == RiskLevel.LOW;
        return new ReviewGuidance(requiresHumanApproval, canAutoApprove, bulkApprovable, risk, factors);
    }

    public ConfidenceThresholds thresholds() {
        return thresholds;
    }

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    /**
     * @param riskFactors human-readable reasons behind the risk level
     */
    public record ReviewGuidance(boolean requiresHumanApproval, boolean canAutoApprove, boolean bulkApprovable,
                                 RiskLevel riskLevel, List<String> riskFactors) {
        public ReviewGuidance {
            Objects.requireNonNull(riskLevel, "riskLevel");
            riskFactors = List.copyOf(riskFactors);
        }
    }
}

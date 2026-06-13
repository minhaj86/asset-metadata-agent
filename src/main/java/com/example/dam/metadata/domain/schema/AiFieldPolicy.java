package com.example.dam.metadata.domain.schema;

/**
 * Governance of AI involvement for one field. Evaluated deterministically by the approval policy; the schema
 * always overrides model confidence.
 *
 * @param enrichable            AI may propose values for this field
 * @param autoApply             AI proposals may be applied without a human when confidence is high enough
 * @param humanApprovalRequired every AI proposal must be approved by a human, regardless of confidence
 */
public record AiFieldPolicy(boolean enrichable, boolean autoApply, boolean humanApprovalRequired) {

    public static final AiFieldPolicy NOT_ENRICHABLE = new AiFieldPolicy(false, false, true);

    public AiFieldPolicy {
        if (autoApply && !enrichable) {
            throw new IllegalArgumentException("autoApply requires enrichable");
        }
        if (autoApply && humanApprovalRequired) {
            throw new IllegalArgumentException("autoApply contradicts humanApprovalRequired");
        }
    }
}

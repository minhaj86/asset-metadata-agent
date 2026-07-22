package com.example.dam.metadata.domain.proposal;

/** Deterministic routing outcome for a machine-generated metadata proposal. */
public enum ApprovalRoute {
    /** Confidence above the auto-approval threshold and the schema permits automatic updates. */
    AUTO_APPROVE,
    /** A human must decide (schema safety rule, overwrite of existing value, or medium confidence). */
    HUMAN_REVIEW,
    /** Below the review threshold: kept as a suggestion, never applied automatically. */
    LOW_CONFIDENCE,
    /** Violates governance or validation rules (e.g. non-enrichable field, invented taxonomy, invalid value). */
    REJECTED_BY_POLICY,
    /** Proposed value equals the current value; nothing to record. */
    NO_CHANGE
}

package com.example.dam.metadata.domain.audit;

public enum AuditAction {
    /** Value stored at asset registration or import. */
    VALUE_IMPORTED,
    /** Value set or replaced by a human through the API. */
    VALUE_SET_MANUALLY,
    /** Value removed by a human. */
    VALUE_REMOVED,
    /** AI proposal applied automatically because policy allowed it. */
    VALUE_AUTO_APPLIED,
    /** AI proposal approved by a human and applied. */
    PROPOSAL_APPROVED,
    /** AI proposal rejected by a human or by deterministic policy. */
    PROPOSAL_REJECTED,
    /** Open proposal closed because a newer proposal or a manual change replaced it. */
    PROPOSAL_SUPERSEDED
}

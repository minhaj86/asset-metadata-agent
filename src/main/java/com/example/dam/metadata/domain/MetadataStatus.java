package com.example.dam.metadata.domain;

/**
 * Trust status of a metadata value or proposal.
 * <ul>
 *   <li>{@link #PROPOSED} - suggested value awaiting a decision (proposals only)</li>
 *   <li>{@link #APPROVED} - confirmed by a human or provided by an authoritative source</li>
 *   <li>{@link #REJECTED} - declined by a human or by deterministic policy (proposals only)</li>
 *   <li>{@link #VALIDATED} - machine-applied value that passed deterministic validation but was not human-reviewed</li>
 * </ul>
 */
public enum MetadataStatus {
    PROPOSED,
    APPROVED,
    REJECTED,
    VALIDATED
}

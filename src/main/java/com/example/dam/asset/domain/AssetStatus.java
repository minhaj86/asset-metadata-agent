package com.example.dam.asset.domain;

/**
 * Lifecycle status derived deterministically from metadata validity and open review work.
 */
public enum AssetStatus {
    /** Registered; mandatory metadata missing or invalid. */
    DRAFT,
    /** AI proposals await a human decision. */
    IN_REVIEW,
    /** All mandatory metadata present and valid, nothing pending. */
    READY,
    /** Withdrawn from use; kept for audit purposes. */
    ARCHIVED
}

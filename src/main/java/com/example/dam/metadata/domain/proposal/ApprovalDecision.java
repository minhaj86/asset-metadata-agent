package com.example.dam.metadata.domain.proposal;

import com.example.dam.metadata.domain.value.MetadataValue;
import java.util.Objects;

/**
 * @param valueToApply value that would be stored if the proposal is applied (merged for repeatable fields)
 */
public record ApprovalDecision(ApprovalRoute route, String reason, MetadataValue valueToApply) {

    public ApprovalDecision {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(reason, "reason");
    }

    public boolean isRecordable() {
        return route != ApprovalRoute.NO_CHANGE;
    }
}

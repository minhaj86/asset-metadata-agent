package com.example.dam.metadata.domain.proposal;

import com.example.dam.metadata.domain.MetadataStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Filter for listing proposals; {@code null} attributes are ignored, so {@code status == null} matches any status.
 *
 * @param assetText     case-insensitive substring of the external asset id
 * @param proposedBy    name of the agent that produced the proposal
 * @param minConfidence inclusive lower confidence bound
 * @param maxConfidence exclusive upper confidence bound
 */
public record ProposalQuery(
        MetadataStatus status,
        ApprovalRoute route,
        UUID assetId,
        String assetText,
        UUID workflowRunId,
        String fieldKey,
        String proposedBy,
        Double minConfidence,
        Double maxConfidence,
        Instant createdAfter,
        int limit) {

    public ProposalQuery {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be within 1..500");
        }
        if (minConfidence != null && (minConfidence < 0.0 || minConfidence > 1.0)
                || maxConfidence != null && (maxConfidence < 0.0 || maxConfidence > 1.0)) {
            throw new IllegalArgumentException("confidence bounds must be within [0,1]");
        }
        assetText = assetText == null || assetText.isBlank() ? null : assetText.strip();
        fieldKey = fieldKey == null || fieldKey.isBlank() ? null : fieldKey.strip();
        proposedBy = proposedBy == null || proposedBy.isBlank() ? null : proposedBy.strip();
    }

    public ProposalQuery(MetadataStatus status, ApprovalRoute route, UUID assetId, int limit) {
        this(status, route, assetId, null, null, null, null, null, null, null, limit);
    }
}

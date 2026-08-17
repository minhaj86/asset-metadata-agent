package com.example.dam.metadata.api;

import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.MetadataStatus;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import com.example.dam.metadata.domain.proposal.MetadataProposal;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MetadataEntryResponse(
        String field,
        @Schema(description = "String, number, boolean, ISO date string or list") Object value,
        MetadataSource source,
        MetadataStatus status,
        @Schema(description = "Model confidence for machine-generated values") Double confidence,
        String aiModel,
        @Schema(description = "Human-confirmed or from a system of record; AI never overwrites it") boolean authoritative,
        Instant updatedAt,
        String updatedBy,
        @Schema(description = "The approved AI proposal this value originates from, if any") OriginResponse origin) {

    /**
     * Provenance of a value that came from an AI proposal.
     *
     * @param editedByReviewer the reviewer changed the proposed value before approving it
     */
    public record OriginResponse(UUID proposalId, String agent, String aiModel, double confidence, String explanation,
                                 List<String> evidence, ApprovalRoute route, String decidedBy, Instant decidedAt,
                                 UUID workflowRunId, boolean editedByReviewer) {
        static OriginResponse from(MetadataProposal proposal) {
            return proposal == null ? null : new OriginResponse(proposal.id(), proposal.proposedBy(),
                    proposal.aiModel(), proposal.confidence(), proposal.explanation(), proposal.evidence(),
                    proposal.route(), proposal.decidedBy(), proposal.decidedAt(), proposal.workflowRunId(),
                    proposal.wasEditedByReviewer());
        }
    }

    static MetadataEntryResponse from(AssetMetadata entry) {
        return from(entry, null);
    }

    static MetadataEntryResponse from(AssetMetadata entry, MetadataProposal origin) {
        return new MetadataEntryResponse(entry.fieldKey(), entry.value().toPlainValue(), entry.source(), entry.status(),
                entry.confidence(), entry.aiModel(), entry.isAuthoritative(), entry.updatedAt(), entry.updatedBy(),
                OriginResponse.from(origin));
    }
}

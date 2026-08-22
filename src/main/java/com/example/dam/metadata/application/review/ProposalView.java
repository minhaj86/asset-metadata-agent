package com.example.dam.metadata.application.review;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.domain.proposal.MetadataProposal;
import com.example.dam.metadata.domain.proposal.ProposalReviewPolicy.ReviewGuidance;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import java.util.Objects;
import java.util.UUID;

/**
 * A proposal together with the context a reviewer needs: the asset it belongs to, the field definition and the
 * deterministic review guidance.
 *
 * @param field definition of the proposed field, {@code null} if it is no longer part of the schema
 */
public record ProposalView(MetadataProposal proposal, AssetRef asset, MetadataFieldDefinition field,
                           ReviewGuidance guidance) {

    public ProposalView {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(guidance, "guidance");
    }

    /**
     * @param externalAssetId {@code null} if the asset no longer exists
     */
    public record AssetRef(UUID id, String externalAssetId, AssetType type, String filename) {
    }
}

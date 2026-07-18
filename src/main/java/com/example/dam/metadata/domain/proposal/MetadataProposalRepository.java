package com.example.dam.metadata.domain.proposal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetadataProposalRepository {

    void insert(MetadataProposal proposal);

    /** Persists a decision (status, decidedAt, decidedBy, comment). */
    void updateDecision(MetadataProposal proposal);

    Optional<MetadataProposal> findById(UUID id);

    List<MetadataProposal> find(ProposalQuery query);

    List<MetadataProposal> findOpenByAssetAndField(UUID assetId, String fieldKey);

    long countOpenByAssetAndRoute(UUID assetId, ApprovalRoute route);

    /** Latest approved proposal per field of an asset: the provenance of AI-originated values. */
    List<MetadataProposal> findLatestApprovedByAsset(UUID assetId);
}

package com.example.dam.metadata.application.review;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.application.review.ProposalView.AssetRef;
import com.example.dam.metadata.domain.proposal.MetadataProposal;
import com.example.dam.metadata.domain.proposal.MetadataProposalRepository;
import com.example.dam.metadata.domain.proposal.ProposalNotFoundException;
import com.example.dam.metadata.domain.proposal.ProposalQuery;
import com.example.dam.metadata.domain.proposal.ProposalReviewPolicy;
import com.example.dam.metadata.domain.proposal.ProposalReviewPolicy.RiskLevel;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the review queue: proposals with asset context, field definitions and review guidance. */
@Service
public class ProposalReviewQueryService {

    private final MetadataProposalRepository proposalRepository;
    private final AssetRepository assetRepository;
    private final MetadataSchemaService schemaService;
    private final ProposalReviewPolicy reviewPolicy;

    public ProposalReviewQueryService(MetadataProposalRepository proposalRepository, AssetRepository assetRepository,
                                      MetadataSchemaService schemaService, ProposalReviewPolicy reviewPolicy) {
        this.proposalRepository = proposalRepository;
        this.assetRepository = assetRepository;
        this.schemaService = schemaService;
        this.reviewPolicy = reviewPolicy;
    }

    /**
     * @param riskLevel optional filter on the derived risk level. It is applied after the query because guidance is
     *                  computed in Java from the schema, so that the policy is defined exactly once.
     */
    @Transactional(readOnly = true)
    public List<ProposalView> list(ProposalQuery query, RiskLevel riskLevel) {
        List<ProposalView> views = views(proposalRepository.find(query));
        return riskLevel == null ? views
                : views.stream().filter(view -> view.guidance().riskLevel() == riskLevel).toList();
    }

    @Transactional(readOnly = true)
    public ProposalView get(UUID proposalId) {
        MetadataProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ProposalNotFoundException(proposalId));
        return view(proposal);
    }

    @Transactional(readOnly = true)
    public ProposalView view(MetadataProposal proposal) {
        return views(List.of(proposal)).getFirst();
    }

    private List<ProposalView> views(List<MetadataProposal> proposals) {
        if (proposals.isEmpty()) {
            return List.of();
        }
        List<UUID> assetIds = proposals.stream().map(MetadataProposal::assetId).distinct().toList();
        Map<UUID, Asset> assets = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::id, Function.identity()));
        return proposals.stream().map(proposal -> view(proposal, assets.get(proposal.assetId()))).toList();
    }

    private ProposalView view(MetadataProposal proposal, Asset asset) {
        Optional<MetadataFieldDefinition> field = asset == null
                ? schemaService.field(proposal.fieldKey())
                : schemaService.schemaFor(asset.type()).field(proposal.fieldKey());
        AssetRef ref = asset == null ? new AssetRef(proposal.assetId(), null, null, null)
                : new AssetRef(asset.id(), asset.externalAssetId(), asset.type(), asset.filename());
        return new ProposalView(proposal, ref, field.orElse(null), reviewPolicy.evaluate(proposal, field));
    }
}

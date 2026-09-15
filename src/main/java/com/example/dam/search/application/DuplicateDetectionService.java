package com.example.dam.search.application;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetNotFoundException;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.search.application.port.AssetEmbeddingRepository;
import com.example.dam.search.application.port.AssetEmbeddingRepository.Neighbour;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Detects duplicate or near-duplicate metadata: vector similarity of the metadata embeddings combined with a
 * deterministic comparison of single-valued searchable fields.
 */
@Service
public class DuplicateDetectionService {

    private static final int CANDIDATES = 10;

    private final AssetRepository assetRepository;
    private final AssetMetadataRepository metadataRepository;
    private final AssetEmbeddingRepository embeddingRepository;
    private final MetadataSchemaService schemaService;
    private final SearchProperties properties;

    public DuplicateDetectionService(AssetRepository assetRepository, AssetMetadataRepository metadataRepository,
                                     AssetEmbeddingRepository embeddingRepository, MetadataSchemaService schemaService,
                                     SearchProperties properties) {
        this.assetRepository = assetRepository;
        this.metadataRepository = metadataRepository;
        this.embeddingRepository = embeddingRepository;
        this.schemaService = schemaService;
        this.properties = properties;
    }

    public enum MatchKind { PROBABLE_DUPLICATE, SIMILAR }

    public record SimilarAssetMatch(Asset asset, double similarity, MatchKind kind, List<String> identicalFields) {
    }

    public List<SimilarAssetMatch> findSimilar(UUID assetId) {
        Asset asset = assetRepository.findById(assetId).orElseThrow(() -> new AssetNotFoundException(assetId));
        List<Neighbour> neighbours = embeddingRepository.findNearest(assetId, CANDIDATES).stream()
                .filter(neighbour -> neighbour.similarity() >= properties.similarSimilarity())
                .toList();
        if (neighbours.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = neighbours.stream().map(Neighbour::assetId).toList();
        Map<UUID, Asset> assets = assetRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Asset::id, Function.identity()));
        Map<UUID, List<AssetMetadata>> metadata = metadataRepository.findByAssetIds(ids);
        AssetMetadataSnapshot reference = AssetMetadataSnapshot.of(assetId, metadataRepository.findByAssetId(assetId));
        MetadataSchema schema = schemaService.schemaFor(asset.type());

        return neighbours.stream()
                .filter(neighbour -> assets.containsKey(neighbour.assetId()))
                .map(neighbour -> {
                    AssetMetadataSnapshot other = AssetMetadataSnapshot.of(neighbour.assetId(),
                            metadata.getOrDefault(neighbour.assetId(), List.of()));
                    MatchKind kind = neighbour.similarity() >= properties.duplicateSimilarity()
                            ? MatchKind.PROBABLE_DUPLICATE : MatchKind.SIMILAR;
                    return new SimilarAssetMatch(assets.get(neighbour.assetId()), neighbour.similarity(), kind,
                            identicalFields(schema, reference, other));
                })
                .toList();
    }

    private static List<String> identicalFields(MetadataSchema schema, AssetMetadataSnapshot a, AssetMetadataSnapshot b) {
        return schema.fieldDefinitions().stream()
                .filter(field -> field.searchable() && !field.repeatable())
                .map(MetadataFieldDefinition::key)
                .filter(key -> a.value(key).isPresent() && b.value(key).isPresent()
                        && a.value(key).get().isEquivalentTo(b.value(key).get()))
                .toList();
    }
}

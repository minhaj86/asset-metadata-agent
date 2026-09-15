package com.example.dam.search.application;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetNotFoundException;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.schema.MetadataDataType;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.value.MetadataValue;
import com.example.dam.search.application.port.AssetEmbeddingRepository;
import com.example.dam.search.application.port.AssetEmbeddingRepository.Neighbour;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Deterministic "assets like this one usually have..." recommendations: values shared by several semantically
 * similar assets are suggested for missing fields. Fields that AI may not touch (rights, product links) are never
 * recommended. Recommendations are read-only hints; applying them is a normal, audited manual update.
 */
@Service
public class MetadataRecommendationService {

    private static final int NEIGHBOURS = 8;
    private static final int MIN_SUPPORT = 2;

    private final AssetRepository assetRepository;
    private final AssetMetadataRepository metadataRepository;
    private final AssetEmbeddingRepository embeddingRepository;
    private final MetadataSchemaService schemaService;
    private final SearchProperties properties;

    public MetadataRecommendationService(AssetRepository assetRepository, AssetMetadataRepository metadataRepository,
                                         AssetEmbeddingRepository embeddingRepository,
                                         MetadataSchemaService schemaService, SearchProperties properties) {
        this.assetRepository = assetRepository;
        this.metadataRepository = metadataRepository;
        this.embeddingRepository = embeddingRepository;
        this.schemaService = schemaService;
        this.properties = properties;
    }

    public record MetadataRecommendation(String field, MetadataValue value, double confidence, int supportingAssets,
                                         List<String> supportingExternalAssetIds, boolean requiresHumanApproval) {
    }

    public List<MetadataRecommendation> recommend(UUID assetId) {
        Asset asset = assetRepository.findById(assetId).orElseThrow(() -> new AssetNotFoundException(assetId));
        MetadataSchema schema = schemaService.schemaFor(asset.type());
        AssetMetadataSnapshot target = AssetMetadataSnapshot.of(assetId, metadataRepository.findByAssetId(assetId));
        List<Neighbour> neighbours = embeddingRepository.findNearest(assetId, NEIGHBOURS).stream()
                .filter(neighbour -> neighbour.similarity() >= properties.recommendationSimilarity())
                .toList();
        if (neighbours.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<AssetMetadata>> metadata = metadataRepository.findByAssetIds(
                neighbours.stream().map(Neighbour::assetId).toList());
        Map<UUID, String> externalIds = new LinkedHashMap<>();
        assetRepository.findAllById(neighbours.stream().map(Neighbour::assetId).toList())
                .forEach(neighbour -> externalIds.put(neighbour.id(), neighbour.externalAssetId()));
        double totalWeight = neighbours.stream().mapToDouble(Neighbour::similarity).sum();

        List<MetadataRecommendation> recommendations = new ArrayList<>();
        for (MetadataFieldDefinition field : schema.fieldDefinitions()) {
            if (target.has(field.key()) || !field.aiEnrichable() || !isCategorical(field)) {
                continue;
            }
            Map<String, Support> supportByValue = new LinkedHashMap<>();
            for (Neighbour neighbour : neighbours) {
                metadata.getOrDefault(neighbour.assetId(), List.of()).stream()
                        .filter(entry -> entry.fieldKey().equals(field.key()))
                        .findFirst()
                        .ifPresent(entry -> supportByValue
                                .computeIfAbsent(MetadataValue.normalize(entry.value().display()),
                                        key -> new Support(entry.value()))
                                .add(neighbour, externalIds.get(neighbour.assetId())));
            }
            supportByValue.values().stream()
                    .filter(support -> support.count() >= MIN_SUPPORT)
                    .max(Comparator.comparingDouble(Support::weight))
                    .ifPresent(best -> recommendations.add(new MetadataRecommendation(field.key(), best.value(),
                            round(best.weight() / totalWeight), best.count(), best.externalIds(),
                            field.aiPolicy().humanApprovalRequired())));
        }
        recommendations.sort(Comparator.comparingDouble(MetadataRecommendation::confidence).reversed());
        return recommendations;
    }

    /**
     * Only categorical attributes transfer between similar assets; free text (titles, alt texts, descriptions)
     * describes one specific image and is never copied.
     */
    private static boolean isCategorical(MetadataFieldDefinition field) {
        return field.hasControlledVocabulary() || field.dataType() == MetadataDataType.BOOLEAN
                || field.repeatable() && field.dataType() == MetadataDataType.STRING;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static final class Support {
        private final MetadataValue value;
        private final List<String> externalIds = new ArrayList<>();
        private double weight;

        private Support(MetadataValue value) {
            this.value = value;
        }

        private void add(Neighbour neighbour, String externalId) {
            weight += neighbour.similarity();
            externalIds.add(externalId);
        }

        MetadataValue value() {
            return value;
        }

        double weight() {
            return weight;
        }

        int count() {
            return externalIds.size();
        }

        List<String> externalIds() {
            return List.copyOf(externalIds);
        }
    }
}

package com.example.dam.metadata.application.catalog;

import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.schema.WellKnownFields;
import com.example.dam.taxonomy.application.TaxonomyService;
import com.example.dam.taxonomy.domain.TaxonomyNode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** How many assets are classified at each taxonomy node (directly and including descendants). */
@Service
public class TaxonomyUsageService {

    private final TaxonomyService taxonomyService;
    private final AssetMetadataRepository metadataRepository;

    public TaxonomyUsageService(TaxonomyService taxonomyService, AssetMetadataRepository metadataRepository) {
        this.taxonomyService = taxonomyService;
        this.metadataRepository = metadataRepository;
    }

    @Transactional(readOnly = true)
    public List<NodeUsage> usage() {
        Map<String, Long> countsByPath = metadataRepository.countAssetsByValue(WellKnownFields.TAXONOMY_PATH);
        return taxonomyService.all().stream().map(node -> usage(node, countsByPath)).toList();
    }

    private static NodeUsage usage(TaxonomyNode node, Map<String, Long> countsByPath) {
        String descendantPrefix = node.path() + TaxonomyNode.PATH_SEPARATOR;
        long total = countsByPath.entrySet().stream()
                .filter(entry -> entry.getKey().equals(node.path()) || entry.getKey().startsWith(descendantPrefix))
                .mapToLong(Map.Entry::getValue)
                .sum();
        return new NodeUsage(node.id(), node.path(), countsByPath.getOrDefault(node.path(), 0L), total);
    }

    /**
     * @param assetCount      assets classified exactly at this node
     * @param totalAssetCount assets classified at this node or any descendant
     */
    public record NodeUsage(String nodeId, String path, long assetCount, long totalAssetCount) {
    }
}

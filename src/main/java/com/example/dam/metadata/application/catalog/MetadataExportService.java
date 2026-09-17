package com.example.dam.metadata.application.catalog;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.common.domain.InvalidRequestException;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exports the current metadata (with provenance) of selected assets. */
@Service
public class MetadataExportService {

    public static final int MAX_ASSETS = 500;

    private final AssetRepository assetRepository;
    private final AssetMetadataRepository metadataRepository;

    public MetadataExportService(AssetRepository assetRepository, AssetMetadataRepository metadataRepository) {
        this.assetRepository = assetRepository;
        this.metadataRepository = metadataRepository;
    }

    @Transactional(readOnly = true)
    public List<AssetExport> export(List<UUID> assetIds) {
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(assetIds));
        if (ids.size() > MAX_ASSETS) {
            throw new InvalidRequestException("EXPORT_LIMIT_EXCEEDED", "At most " + MAX_ASSETS + " assets per export");
        }
        Map<UUID, List<AssetMetadata>> metadata = metadataRepository.findByAssetIds(ids);
        return assetRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(Asset::externalAssetId))
                .map(asset -> new AssetExport(asset, metadata.getOrDefault(asset.id(), List.of())))
                .toList();
    }

    public record AssetExport(Asset asset, List<AssetMetadata> metadata) {
        public AssetExport {
            metadata = List.copyOf(metadata);
        }
    }
}

package com.example.dam.search.application;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.search.application.port.AssetEmbeddingRepository;
import com.example.dam.search.application.port.TextEmbedder;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Keeps the semantic index in sync with metadata. Indexing failures never affect metadata writes; stale or
 * missing embeddings are repaired by {@link #reindexStale(int)}.
 */
@Service
public class AssetIndexingService {

    private static final Logger log = LoggerFactory.getLogger(AssetIndexingService.class);

    private final AssetRepository assetRepository;
    private final AssetMetadataRepository metadataRepository;
    private final MetadataSchemaService schemaService;
    private final EmbeddingDocumentBuilder documentBuilder;
    private final ObjectProvider<TextEmbedder> embedder;
    private final AssetEmbeddingRepository embeddingRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public AssetIndexingService(AssetRepository assetRepository, AssetMetadataRepository metadataRepository,
                                MetadataSchemaService schemaService, EmbeddingDocumentBuilder documentBuilder,
                                ObjectProvider<TextEmbedder> embedder, AssetEmbeddingRepository embeddingRepository,
                                MeterRegistry meterRegistry, Clock clock) {
        this.assetRepository = assetRepository;
        this.metadataRepository = metadataRepository;
        this.schemaService = schemaService;
        this.documentBuilder = documentBuilder;
        this.embedder = embedder;
        this.embeddingRepository = embeddingRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /** @return {@code true} if the asset was (re-)indexed */
    public boolean index(UUID assetId) {
        TextEmbedder textEmbedder = embedder.getIfAvailable();
        Optional<Asset> asset = assetRepository.findById(assetId);
        if (textEmbedder == null || asset.isEmpty()) {
            return false;
        }
        try {
            AssetMetadataSnapshot metadata = AssetMetadataSnapshot.of(assetId, metadataRepository.findByAssetId(assetId));
            String text = documentBuilder.build(asset.get(), schemaService.schemaFor(asset.get().type()), metadata);
            embeddingRepository.upsert(assetId, textEmbedder.embed(text), text, textEmbedder.modelName(),
                    asset.get().metadataRevision(), clock.instant());
            meterRegistry.counter("dam.search.indexing", "outcome", "success").increment();
            return true;
        } catch (RuntimeException e) {
            meterRegistry.counter("dam.search.indexing", "outcome", "failure").increment();
            log.warn("Indexing of asset {} failed (will be retried by the reindex job): {}", assetId, e.getMessage());
            return false;
        }
    }

    public int reindexStale(int batchSize) {
        TextEmbedder textEmbedder = embedder.getIfAvailable();
        if (textEmbedder == null) {
            return 0;
        }
        List<UUID> stale = embeddingRepository.findStaleAssetIds(textEmbedder.modelName(), batchSize);
        int indexed = (int) stale.stream().filter(this::index).count();
        if (indexed > 0) {
            log.info("Reindexed {} stale asset embedding(s)", indexed);
        }
        return indexed;
    }
}

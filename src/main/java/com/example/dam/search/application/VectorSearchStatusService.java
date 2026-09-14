package com.example.dam.search.application;

import com.example.dam.search.application.port.AssetEmbeddingRepository;
import com.example.dam.search.application.port.AssetEmbeddingRepository.Coverage;
import com.example.dam.search.application.port.TextEmbedder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Reports whether semantic (vector) search is usable and how much of the catalog is indexed. */
@Service
public class VectorSearchStatusService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchStatusService.class);

    private final ObjectProvider<TextEmbedder> embedder;
    private final AssetEmbeddingRepository embeddingRepository;

    public VectorSearchStatusService(ObjectProvider<TextEmbedder> embedder,
                                     AssetEmbeddingRepository embeddingRepository) {
        this.embedder = embedder;
        this.embeddingRepository = embeddingRepository;
    }

    public VectorSearchStatus status() {
        TextEmbedder textEmbedder = embedder.getIfAvailable();
        if (textEmbedder == null) {
            return new VectorSearchStatus(State.DISABLED, null, null, null,
                    "Embedding model disabled; structured search only");
        }
        try {
            Coverage coverage = embeddingRepository.coverage(textEmbedder.modelName());
            return new VectorSearchStatus(State.AVAILABLE, textEmbedder.modelName(), textEmbedder.dimension(), coverage,
                    "%d of %d assets indexed".formatted(coverage.indexedAssets(), coverage.totalAssets()));
        } catch (RuntimeException e) {
            log.warn("Vector store status check failed: {}", e.toString());
            return new VectorSearchStatus(State.UNAVAILABLE, textEmbedder.modelName(), textEmbedder.dimension(), null,
                    "Vector store unavailable; structured search is still available");
        }
    }

    public enum State { AVAILABLE, UNAVAILABLE, DISABLED }

    /**
     * @param coverage {@code null} when the vector store could not be queried
     */
    public record VectorSearchStatus(State state, String embeddingModel, Integer dimension, Coverage coverage,
                                     String detail) {
    }
}

package com.example.dam.ai.embedding;

import com.example.dam.ai.config.AiProperties;
import com.example.dam.ai.config.ConditionalOnAiEnabled;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Fails fast when the embedding model, the configuration and the vector columns disagree on the dimension,
 * instead of failing later on every insert.
 */
@Component
@ConditionalOnAiEnabled
class EmbeddingDimensionVerifier implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingDimensionVerifier.class);
    private static final List<String> VECTOR_TABLES = List.of("asset_embeddings", "knowledge_embeddings");

    private final EmbeddingModel embeddingModel;
    private final AiProperties properties;
    private final JdbcClient jdbc;

    EmbeddingDimensionVerifier(EmbeddingModel embeddingModel, AiProperties properties, JdbcClient jdbc) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    @Override
    public void afterSingletonsInstantiated() {
        int modelDimension = embeddingModel.dimension();
        int configured = properties.embedding().dimension();
        if (modelDimension != configured) {
            throw new IllegalStateException(("Embedding model produces %d-dimensional vectors but "
                    + "ai.embedding.dimension is %d. Set AI_EMBEDDING_DIMENSION=%d and start with an empty database.")
                    .formatted(modelDimension, configured, modelDimension));
        }
        for (String table : VECTOR_TABLES) {
            Integer columnDimension = jdbc.sql("""
                            SELECT atttypmod FROM pg_attribute
                             WHERE attrelid = CAST(:table AS regclass) AND attname = 'embedding'""")
                    .param("table", table)
                    .query(Integer.class)
                    .optional()
                    .orElse(null);
            if (columnDimension != null && columnDimension != modelDimension) {
                throw new IllegalStateException(("Column %s.embedding has dimension %d but the embedding model "
                        + "produces %d. Recreate the database (docker compose down -v) after changing the model.")
                        .formatted(table, columnDimension, modelDimension));
            }
        }
        log.info("Embedding model verified: {} dimensions ({})", modelDimension, properties.embedding().provider());
    }
}

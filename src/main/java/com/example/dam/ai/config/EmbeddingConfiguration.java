package com.example.dam.ai.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding model and the pgvector-backed knowledge store. The default is the in-process all-MiniLM-L6-v2 model,
 * so semantic search and RAG work offline without any API key.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAiEnabled
class EmbeddingConfiguration {

    static final String KNOWLEDGE_TABLE = "knowledge_embeddings";

    @Bean
    EmbeddingModel embeddingModel(AiProperties properties, AiModelInfo modelInfo) {
        return switch (properties.embedding().provider()) {
            case LOCAL -> new AllMiniLmL6V2EmbeddingModel();
            case OPENAI -> OpenAiEmbeddingModel.builder()
                    .apiKey(properties.openai().apiKey())
                    .modelName(modelInfo.embeddingModel())
                    .dimensions(properties.embedding().dimension())
                    .timeout(properties.chat().timeout())
                    .build();
            case OLLAMA -> OllamaEmbeddingModel.builder()
                    .baseUrl(properties.ollama().baseUrl())
                    .modelName(modelInfo.embeddingModel())
                    .timeout(properties.chat().timeout())
                    .build();
        };
    }

    /** Flyway owns the table; the store neither creates tables nor extensions. */
    @Bean
    EmbeddingStore<TextSegment> knowledgeEmbeddingStore(DataSource dataSource, AiProperties properties) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(KNOWLEDGE_TABLE)
                .dimension(properties.embedding().dimension())
                .createTable(false)
                .useIndex(false)
                .skipCreateVectorExtension(true)
                .metadataStorageConfig(DefaultMetadataStorageConfig.builder()
                        .storageMode(MetadataStorageMode.COMBINED_JSONB)
                        .columnDefinitions(List.of("metadata JSONB NULL"))
                        .build())
                .build();
    }
}

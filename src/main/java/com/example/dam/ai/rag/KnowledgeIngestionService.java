package com.example.dam.ai.rag;

import com.example.dam.ai.config.AiModelInfo;
import com.example.dam.ai.config.AiProperties;
import com.example.dam.ai.config.ConditionalOnAiEnabled;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * RAG ingestion pipeline: markdown documents -> section chunks -> embeddings -> pgvector. Idempotent: a document
 * is re-ingested only when its content hash or the embedding model changed. Failures never stop the application.
 */
@Component
@Order(5)
@ConditionalOnAiEnabled
class KnowledgeIngestionService implements ApplicationRunner {

    static final String DOCUMENT_ID = "documentId";
    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final ResourcePatternResolver resources;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;
    private final KnowledgeDocumentRegistry registry;
    private final AiProperties properties;
    private final AiModelInfo modelInfo;
    private final Clock clock;
    private final MarkdownSectionSplitter splitter = new MarkdownSectionSplitter();

    KnowledgeIngestionService(ResourcePatternResolver resources, EmbeddingModel embeddingModel,
                              EmbeddingStore<TextSegment> store, KnowledgeDocumentRegistry registry,
                              AiProperties properties, AiModelInfo modelInfo, Clock clock) {
        this.resources = resources;
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.registry = registry;
        this.properties = properties;
        this.modelInfo = modelInfo;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.knowledge().ingestOnStartup()) {
            ingestAll();
        }
    }

    public int ingestAll() {
        int ingested = 0;
        try {
            for (Resource resource : resources.getResources(properties.knowledge().location())) {
                if (ingest(resource)) {
                    ingested++;
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Knowledge ingestion failed; RAG answers may be incomplete: {}", e.getMessage());
        }
        if (ingested > 0) {
            log.info("Ingested {} knowledge document(s) into the vector store", ingested);
        }
        return ingested;
    }

    private boolean ingest(Resource resource) throws IOException {
        String filename = resource.getFilename();
        if (filename == null) {
            return false;
        }
        String id = filename.replaceFirst("\\.md$", "");
        String text = resource.getContentAsString(StandardCharsets.UTF_8);
        String hash = sha256(text);
        boolean upToDate = registry.find(id)
                .filter(existing -> existing.contentHash().equals(hash))
                .filter(existing -> existing.embeddingModel().equals(modelInfo.embeddingModel()))
                .isPresent();
        if (upToDate) {
            return false;
        }
        String title = text.lines().filter(line -> line.startsWith("# ")).findFirst()
                .map(line -> line.substring(2).strip()).orElse(id);
        Document document = Document.from(text, Metadata.from(Map.of(
                DOCUMENT_ID, id, MarkdownSectionSplitter.TITLE, title, "source", "knowledge/" + filename)));

        store.removeAll(MetadataFilterBuilder.metadataKey(DOCUMENT_ID).isEqualTo(id));
        int chunks = splitter.split(document).size();
        EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(document);
        registry.upsert(id, title, "knowledge/" + filename, hash, chunks, modelInfo.embeddingModel(), clock.instant());
        log.info("Knowledge document '{}' ingested ({} chunks)", id, chunks);
        return true;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

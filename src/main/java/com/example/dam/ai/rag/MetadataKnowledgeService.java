package com.example.dam.ai.rag;

import com.example.dam.ai.config.AiProperties;
import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.knowledge.application.port.KnowledgeBase;
import com.example.dam.metadata.application.enrichment.AssetEnrichmentContext;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Semantic retrieval over the metadata governance knowledge base (pgvector via LangChain4j). Agents obtain rules
 * from here instead of carrying large hard-coded business rules in their prompts.
 */
@Service
@ConditionalOnAiEnabled
public class MetadataKnowledgeService implements KnowledgeBase {

    private static final Logger log = LoggerFactory.getLogger(MetadataKnowledgeService.class);

    private final EmbeddingStore<TextSegment> store;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeDocumentRegistry registry;
    private final AiProperties properties;

    MetadataKnowledgeService(EmbeddingStore<TextSegment> store, EmbeddingModel embeddingModel,
                             KnowledgeDocumentRegistry registry, AiProperties properties) {
        this.store = store;
        this.embeddingModel = embeddingModel;
        this.registry = registry;
        this.properties = properties;
    }

    @Override
    public List<KnowledgeSnippet> search(String query, int maxResults) {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(query).content())
                .maxResults(maxResults)
                .minScore(properties.knowledge().minScore())
                .build();
        return store.search(request).matches().stream().map(MetadataKnowledgeService::snippet).toList();
    }

    @Override
    public List<KnowledgeDocumentInfo> documents() {
        return registry.findAll();
    }

    /**
     * Targeted retrieval of governance guidance for an enrichment run. Returns an empty string (and logs) when
     * the knowledge base is unavailable so that enrichment can continue without it.
     */
    public String guidanceFor(AssetEnrichmentContext context) {
        return guidanceFor(context.asset().type());
    }

    public String guidanceFor(AssetType assetType) {
        String query = "Metadata rules for %s assets: mandatory fields, alt text, title, keywords and taxonomy"
                .formatted(assetType);
        try {
            return search(query, properties.knowledge().maxResults()).stream()
                    .map(snippet -> "[%s - %s]%n%s".formatted(snippet.title(), snippet.section(), body(snippet.text())))
                    .collect(Collectors.joining("\n\n"));
        } catch (RuntimeException e) {
            log.warn("Governance guidance unavailable: {}", e.getMessage());
            return "";
        }
    }

    /** LangChain4j content retriever for retrieval-augmented AI services. */
    public ContentRetriever contentRetriever() {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(properties.knowledge().maxResults())
                .minScore(properties.knowledge().minScore())
                .build();
    }

    private static KnowledgeSnippet snippet(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        return new KnowledgeSnippet(
                segment.metadata().getString(KnowledgeIngestionService.DOCUMENT_ID),
                segment.metadata().getString(MarkdownSectionSplitter.TITLE),
                segment.metadata().getString(MarkdownSectionSplitter.SECTION),
                body(segment.text()),
                Math.round(match.score() * 1000) / 1000.0);
    }

    /** Removes the "document - section" retrieval prefix line. */
    private static String body(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(newline + 1);
    }
}

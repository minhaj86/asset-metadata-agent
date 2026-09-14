package com.example.dam.knowledge.application.port;

import java.time.Instant;
import java.util.List;

/** Port to the metadata-governance knowledge base (RAG retrieval, implemented in the AI layer). */
public interface KnowledgeBase {

    List<KnowledgeSnippet> search(String query, int maxResults);

    List<KnowledgeDocumentInfo> documents();

    record KnowledgeSnippet(String documentId, String title, String section, String text, double score) {
    }

    record KnowledgeDocumentInfo(String id, String title, String source, int chunks, String embeddingModel,
                                 Instant ingestedAt) {
    }
}

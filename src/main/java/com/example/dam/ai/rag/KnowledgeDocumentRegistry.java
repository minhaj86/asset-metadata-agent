package com.example.dam.ai.rag;

import static com.example.dam.common.persistence.SqlValues.instant;
import static com.example.dam.common.persistence.SqlValues.timestamptz;

import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.knowledge.application.port.KnowledgeBase.KnowledgeDocumentInfo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Registry of ingested knowledge documents (content hash per document makes ingestion idempotent). */
@Repository
@ConditionalOnAiEnabled
class KnowledgeDocumentRegistry {

    record RegisteredDocument(String id, String contentHash, String embeddingModel) {
    }

    private final JdbcClient jdbc;

    KnowledgeDocumentRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<RegisteredDocument> find(String id) {
        return jdbc.sql("SELECT id, content_hash, embedding_model FROM knowledge_documents WHERE id = :id")
                .param("id", id)
                .query((rs, row) -> new RegisteredDocument(rs.getString("id"), rs.getString("content_hash"),
                        rs.getString("embedding_model")))
                .optional();
    }

    void upsert(String id, String title, String source, String contentHash, int chunks, String embeddingModel,
                Instant now) {
        jdbc.sql("""
                        INSERT INTO knowledge_documents (id, title, source, content_hash, chunk_count, embedding_model,
                                                         ingested_at)
                        VALUES (:id, :title, :source, :hash, :chunks, :model, :now)
                        ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, source = EXCLUDED.source,
                            content_hash = EXCLUDED.content_hash, chunk_count = EXCLUDED.chunk_count,
                            embedding_model = EXCLUDED.embedding_model, ingested_at = EXCLUDED.ingested_at""")
                .param("id", id)
                .param("title", title)
                .param("source", source)
                .param("hash", contentHash)
                .param("chunks", chunks)
                .param("model", embeddingModel)
                .param("now", timestamptz(now))
                .update();
    }

    List<KnowledgeDocumentInfo> findAll() {
        return jdbc.sql("SELECT * FROM knowledge_documents ORDER BY id")
                .query((rs, row) -> new KnowledgeDocumentInfo(rs.getString("id"), rs.getString("title"),
                        rs.getString("source"), rs.getInt("chunk_count"), rs.getString("embedding_model"),
                        instant(rs, "ingested_at")))
                .list();
    }
}

package com.example.dam.search.infrastructure;

import static com.example.dam.common.persistence.SqlValues.timestamptz;
import static com.example.dam.common.persistence.SqlValues.uuid;

import com.example.dam.search.application.port.AssetEmbeddingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAssetEmbeddingRepository implements AssetEmbeddingRepository {

    private final JdbcClient jdbc;

    JdbcAssetEmbeddingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(UUID assetId, float[] vector, String text, String model, long metadataRevision, Instant indexedAt) {
        jdbc.sql("""
                        INSERT INTO asset_embeddings (asset_id, embedding, embedding_text, embedding_model,
                                                      metadata_revision, indexed_at)
                        VALUES (:assetId, CAST(:vector AS vector), :text, :model, :revision, :indexedAt)
                        ON CONFLICT (asset_id) DO UPDATE SET
                            embedding = EXCLUDED.embedding,
                            embedding_text = EXCLUDED.embedding_text,
                            embedding_model = EXCLUDED.embedding_model,
                            metadata_revision = EXCLUDED.metadata_revision,
                            indexed_at = EXCLUDED.indexed_at
                        WHERE asset_embeddings.metadata_revision <= EXCLUDED.metadata_revision
                           OR asset_embeddings.embedding_model <> EXCLUDED.embedding_model""")
                .param("assetId", assetId)
                .param("vector", PgVectors.toLiteral(vector))
                .param("text", text)
                .param("model", model)
                .param("revision", metadataRevision)
                .param("indexedAt", timestamptz(indexedAt))
                .update();
    }

    @Override
    public Optional<float[]> findVector(UUID assetId) {
        return jdbc.sql("SELECT CAST(embedding AS text) FROM asset_embeddings WHERE asset_id = :assetId")
                .param("assetId", assetId)
                .query(String.class)
                .optional()
                .map(PgVectors::fromLiteral);
    }

    @Override
    public List<UUID> findStaleAssetIds(String currentModel, int limit) {
        return jdbc.sql("""
                        SELECT a.id
                          FROM assets a
                          LEFT JOIN asset_embeddings e ON e.asset_id = a.id
                         WHERE e.asset_id IS NULL
                            OR e.metadata_revision < a.metadata_revision
                            OR e.embedding_model <> :model
                         ORDER BY a.updated_at
                         LIMIT :limit""")
                .param("model", currentModel)
                .param("limit", limit)
                .query((rs, row) -> uuid(rs, "id"))
                .list();
    }

    @Override
    public List<Neighbour> findNearest(UUID assetId, int limit) {
        return jdbc.sql("""
                        SELECT other.asset_id, 1 - (other.embedding <=> ref.embedding) AS similarity
                          FROM asset_embeddings ref
                          JOIN asset_embeddings other ON other.asset_id <> ref.asset_id
                         WHERE ref.asset_id = :assetId
                         ORDER BY other.embedding <=> ref.embedding
                         LIMIT :limit""")
                .param("assetId", assetId)
                .param("limit", limit)
                .query((rs, row) -> new Neighbour(uuid(rs, "asset_id"), rs.getDouble("similarity")))
                .list();
    }

    @Override
    public Coverage coverage(String currentModel) {
        return jdbc.sql("""
                        SELECT (SELECT count(*) FROM assets) AS total,
                               (SELECT count(*) FROM asset_embeddings e JOIN assets a ON a.id = e.asset_id
                                 WHERE e.embedding_model = :model AND e.metadata_revision >= a.metadata_revision)
                                   AS indexed""")
                .param("model", currentModel)
                .query((rs, row) -> new Coverage(rs.getLong("indexed"), rs.getLong("total")))
                .single();
    }
}

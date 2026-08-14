package com.example.dam.metadata.infrastructure.persistence;

import static com.example.dam.common.persistence.SqlValues.confidence;
import static com.example.dam.common.persistence.SqlValues.instant;
import static com.example.dam.common.persistence.SqlValues.nullableDouble;
import static com.example.dam.common.persistence.SqlValues.timestamptz;
import static com.example.dam.common.persistence.SqlValues.uuid;

import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.MetadataStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAssetMetadataRepository implements AssetMetadataRepository {

    private final JdbcClient jdbc;
    private final MetadataValueJsonCodec codec;

    JdbcAssetMetadataRepository(JdbcClient jdbc, MetadataValueJsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public List<AssetMetadata> findByAssetId(UUID assetId) {
        return jdbc.sql("SELECT * FROM asset_metadata WHERE asset_id = :assetId ORDER BY field_key")
                .param("assetId", assetId)
                .query(this::map)
                .list();
    }

    @Override
    public Map<UUID, List<AssetMetadata>> findByAssetIds(Collection<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("SELECT * FROM asset_metadata WHERE asset_id IN (:assetIds) ORDER BY asset_id, field_key")
                .param("assetIds", assetIds)
                .query(this::map)
                .list()
                .stream()
                .collect(Collectors.groupingBy(AssetMetadata::assetId, LinkedHashMap::new, Collectors.toList()));
    }

    @Override
    public void upsert(AssetMetadata metadata) {
        jdbc.sql("""
                        INSERT INTO asset_metadata (asset_id, field_key, value, source, confidence, status, ai_model,
                                                    created_at, updated_at, updated_by)
                        VALUES (:assetId, :fieldKey, CAST(:value AS jsonb), :source, :confidence, :status, :aiModel,
                                :createdAt, :updatedAt, :updatedBy)
                        ON CONFLICT (asset_id, field_key) DO UPDATE SET
                            value = EXCLUDED.value,
                            source = EXCLUDED.source,
                            confidence = EXCLUDED.confidence,
                            status = EXCLUDED.status,
                            ai_model = EXCLUDED.ai_model,
                            updated_at = EXCLUDED.updated_at,
                            updated_by = EXCLUDED.updated_by""")
                .param("assetId", metadata.assetId())
                .param("fieldKey", metadata.fieldKey())
                .param("value", codec.toJson(metadata.value()))
                .param("source", metadata.source().name())
                .param("confidence", confidence(metadata.confidence()))
                .param("status", metadata.status().name())
                .param("aiModel", metadata.aiModel())
                .param("createdAt", timestamptz(metadata.createdAt()))
                .param("updatedAt", timestamptz(metadata.updatedAt()))
                .param("updatedBy", metadata.updatedBy())
                .update();
    }

    @Override
    public boolean delete(UUID assetId, String fieldKey) {
        return jdbc.sql("DELETE FROM asset_metadata WHERE asset_id = :assetId AND field_key = :fieldKey")
                .param("assetId", assetId)
                .param("fieldKey", fieldKey)
                .update() > 0;
    }

    private AssetMetadata map(ResultSet rs, int row) throws SQLException {
        String fieldKey = rs.getString("field_key");
        return new AssetMetadata(
                uuid(rs, "asset_id"),
                fieldKey,
                codec.fromJson(fieldKey, rs.getString("value")),
                MetadataSource.valueOf(rs.getString("source")),
                nullableDouble(rs, "confidence"),
                MetadataStatus.valueOf(rs.getString("status")),
                rs.getString("ai_model"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                rs.getString("updated_by"));
    }

    @Override
    public Map<String, Long> countAssetsByValue(String fieldKey) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT value #>> '{}' AS value_text, count(*) AS assets
                          FROM asset_metadata
                         WHERE field_key = :fieldKey AND jsonb_typeof(value) <> 'array'
                         GROUP BY value_text
                         ORDER BY value_text""")
                .param("fieldKey", fieldKey)
                .query(rs -> {
                    counts.put(rs.getString("value_text"), rs.getLong("assets"));
                });
        return counts;
    }
}

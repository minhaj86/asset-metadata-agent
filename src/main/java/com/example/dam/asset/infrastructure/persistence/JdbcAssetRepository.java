package com.example.dam.asset.infrastructure.persistence;

import static com.example.dam.common.persistence.SqlValues.instant;
import static com.example.dam.common.persistence.SqlValues.timestamptz;
import static com.example.dam.common.persistence.SqlValues.uuid;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.asset.domain.AssetType;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAssetRepository implements AssetRepository {

    private static final String COLUMNS = """
            id, external_asset_id, filename, mime_type, asset_url, asset_type, status, metadata_revision,
            created_at, updated_at""";

    private final JdbcClient jdbc;

    JdbcAssetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Asset asset) {
        jdbc.sql("""
                        INSERT INTO assets (id, external_asset_id, filename, mime_type, asset_url, asset_type, status,
                                            metadata_revision, created_at, updated_at)
                        VALUES (:id, :externalAssetId, :filename, :mimeType, :assetUrl, :type, :status,
                                :metadataRevision, :createdAt, :updatedAt)""")
                .param("id", asset.id())
                .param("externalAssetId", asset.externalAssetId())
                .param("filename", asset.filename())
                .param("mimeType", asset.mimeType())
                .param("assetUrl", asset.assetUrl().toString())
                .param("type", asset.type().name())
                .param("status", asset.status().name())
                .param("metadataRevision", asset.metadataRevision())
                .param("createdAt", timestamptz(asset.createdAt()))
                .param("updatedAt", timestamptz(asset.updatedAt()))
                .update();
    }

    @Override
    public Optional<Asset> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM assets WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<Asset> findByExternalAssetId(String externalAssetId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM assets WHERE external_asset_id = :externalAssetId")
                .param("externalAssetId", externalAssetId)
                .query(this::map)
                .optional();
    }

    @Override
    public List<Asset> findAllById(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + COLUMNS + " FROM assets WHERE id IN (:ids)")
                .param("ids", ids)
                .query(this::map)
                .list();
    }

    @Override
    public List<Asset> findPage(AssetType type, int offset, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM assets
                        WHERE (CAST(:type AS VARCHAR) IS NULL OR asset_type = :type)
                        ORDER BY created_at, id
                        OFFSET :offset LIMIT :limit""")
                .param("type", type == null ? null : type.name())
                .param("offset", offset)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    @Override
    public Optional<Asset> lockById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM assets WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    public void updateStatus(UUID id, AssetStatus status, Instant now) {
        jdbc.sql("UPDATE assets SET status = :status, updated_at = :now WHERE id = :id AND status <> :status")
                .param("status", status.name())
                .param("now", timestamptz(now))
                .param("id", id)
                .update();
    }

    @Override
    public long incrementMetadataRevision(UUID id, Instant now) {
        return jdbc.sql("""
                        UPDATE assets SET metadata_revision = metadata_revision + 1, updated_at = :now
                         WHERE id = :id
                        RETURNING metadata_revision""")
                .param("now", timestamptz(now))
                .param("id", id)
                .query(Long.class)
                .single();
    }

    private Asset map(ResultSet rs, int row) throws SQLException {
        return new Asset(
                uuid(rs, "id"),
                rs.getString("external_asset_id"),
                rs.getString("filename"),
                rs.getString("mime_type"),
                URI.create(rs.getString("asset_url")),
                AssetType.valueOf(rs.getString("asset_type")),
                AssetStatus.valueOf(rs.getString("status")),
                rs.getLong("metadata_revision"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }
}

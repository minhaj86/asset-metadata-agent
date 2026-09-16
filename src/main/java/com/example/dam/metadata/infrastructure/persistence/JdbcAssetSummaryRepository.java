package com.example.dam.metadata.infrastructure.persistence;

import static com.example.dam.common.persistence.SqlValues.instant;
import static com.example.dam.common.persistence.SqlValues.timestamptz;
import static com.example.dam.common.persistence.SqlValues.uuid;

import com.example.dam.metadata.application.catalog.AssetMetadataSummary;
import com.example.dam.metadata.application.catalog.AssetSummaryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcAssetSummaryRepository implements AssetSummaryRepository {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    JdbcAssetSummaryRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void upsert(AssetMetadataSummary summary) {
        jdbc.sql("""
                        INSERT INTO asset_metadata_summaries (asset_id, schema_version, valid, completeness,
                               required_completeness, quality_score, missing_required, missing_required_count,
                               error_count, warning_count, ai_value_count, pending_review_count, low_confidence_count,
                               usage_rights_end, refreshed_at)
                        VALUES (:assetId, :schemaVersion, :valid, :completeness, :requiredCompleteness, :qualityScore,
                                CAST(:missingRequired AS jsonb), :missingRequiredCount, :errorCount, :warningCount,
                                :aiValueCount, :pendingReviewCount, :lowConfidenceCount, :usageRightsEnd, :refreshedAt)
                        ON CONFLICT (asset_id) DO UPDATE SET
                               schema_version = EXCLUDED.schema_version, valid = EXCLUDED.valid,
                               completeness = EXCLUDED.completeness,
                               required_completeness = EXCLUDED.required_completeness,
                               quality_score = EXCLUDED.quality_score, missing_required = EXCLUDED.missing_required,
                               missing_required_count = EXCLUDED.missing_required_count,
                               error_count = EXCLUDED.error_count, warning_count = EXCLUDED.warning_count,
                               ai_value_count = EXCLUDED.ai_value_count,
                               pending_review_count = EXCLUDED.pending_review_count,
                               low_confidence_count = EXCLUDED.low_confidence_count,
                               usage_rights_end = EXCLUDED.usage_rights_end, refreshed_at = EXCLUDED.refreshed_at""")
                .param("assetId", summary.assetId())
                .param("schemaVersion", summary.schemaVersion())
                .param("valid", summary.valid())
                .param("completeness", percentage(summary.completeness()))
                .param("requiredCompleteness", percentage(summary.requiredCompleteness()))
                .param("qualityScore", summary.qualityScore())
                .param("missingRequired", jsonMapper.writeValueAsString(summary.missingRequired()))
                .param("missingRequiredCount", summary.missingRequired().size())
                .param("errorCount", summary.errorCount())
                .param("warningCount", summary.warningCount())
                .param("aiValueCount", summary.aiValueCount())
                .param("pendingReviewCount", summary.pendingReviewCount())
                .param("lowConfidenceCount", summary.lowConfidenceCount())
                .param("usageRightsEnd", summary.usageRightsEnd() == null ? null : Date.valueOf(summary.usageRightsEnd()))
                .param("refreshedAt", timestamptz(summary.refreshedAt()))
                .update();
    }

    @Override
    public Optional<AssetMetadataSummary> findByAssetId(UUID assetId) {
        return jdbc.sql("SELECT * FROM asset_metadata_summaries WHERE asset_id = :assetId")
                .param("assetId", assetId)
                .query((rs, row) -> map(rs, jsonMapper))
                .optional();
    }

    @Override
    public List<UUID> findAssetIdsWithoutSummary(int limit) {
        return jdbc.sql("""
                        SELECT a.id FROM assets a
                         WHERE NOT EXISTS (SELECT 1 FROM asset_metadata_summaries s WHERE s.asset_id = a.id)
                         ORDER BY a.created_at
                         LIMIT :limit""")
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }

    /** Maps the summary columns of a result set; returns {@code null} when the row has no summary (outer join). */
    static AssetMetadataSummary map(ResultSet rs, JsonMapper jsonMapper) throws SQLException {
        if (rs.getObject("refreshed_at") == null) {
            return null;
        }
        Date rightsEnd = rs.getDate("usage_rights_end");
        return new AssetMetadataSummary(
                uuid(rs, "asset_id"),
                rs.getInt("schema_version"),
                rs.getBoolean("valid"),
                rs.getBigDecimal("completeness").doubleValue(),
                rs.getBigDecimal("required_completeness").doubleValue(),
                rs.getInt("quality_score"),
                jsonMapper.readValue(rs.getString("missing_required"),
                        jsonMapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                rs.getInt("error_count"),
                rs.getInt("warning_count"),
                rs.getInt("ai_value_count"),
                rs.getInt("pending_review_count"),
                rs.getInt("low_confidence_count"),
                rightsEnd == null ? null : rightsEnd.toLocalDate(),
                instant(rs, "refreshed_at"));
    }

    private static BigDecimal percentage(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}

package com.example.dam.metadata.infrastructure.persistence;

import static com.example.dam.common.persistence.SqlValues.instant;
import static com.example.dam.common.persistence.SqlValues.timestamptz;
import static com.example.dam.common.persistence.SqlValues.uuid;

import com.example.dam.metadata.application.enrichment.EnrichmentRun;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunStatus;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunSummary;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.StageState;
import com.example.dam.metadata.application.enrichment.EnrichmentRunRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcEnrichmentRunRepository implements EnrichmentRunRepository {

    private static final String SELECT = """
            SELECT r.*, a.external_asset_id
              FROM enrichment_runs r
              LEFT JOIN assets a ON a.id = r.asset_id
            """;

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    JdbcEnrichmentRunRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void insert(EnrichmentRun run) {
        jdbc.sql("""
                        INSERT INTO enrichment_runs (id, asset_id, status, requested_by, ai_model, correlation_id,
                                created_at, started_at, finished_at, stages, summary, warnings, error_code, error_message)
                        VALUES (:id, :assetId, :status, :requestedBy, :aiModel, :correlationId, :createdAt, :startedAt,
                                :finishedAt, CAST(:stages AS jsonb), CAST(:summary AS jsonb), CAST(:warnings AS jsonb),
                                :errorCode, :errorMessage)""")
                .param("id", run.id())
                .param("assetId", run.assetId())
                .param("requestedBy", run.requestedBy())
                .param("aiModel", run.aiModel())
                .param("correlationId", run.correlationId())
                .param("createdAt", timestamptz(run.createdAt()))
                .params(mutableParams(run))
                .update();
    }

    @Override
    public void update(EnrichmentRun run) {
        jdbc.sql("""
                        UPDATE enrichment_runs
                           SET status = :status, started_at = :startedAt, finished_at = :finishedAt,
                               stages = CAST(:stages AS jsonb), summary = CAST(:summary AS jsonb),
                               warnings = CAST(:warnings AS jsonb), error_code = :errorCode, error_message = :errorMessage
                         WHERE id = :id""")
                .param("id", run.id())
                .params(mutableParams(run))
                .update();
    }

    @Override
    public Optional<EnrichmentRun> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE r.id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    public List<EnrichmentRun> findRecent(UUID assetId, RunStatus status, int limit) {
        return jdbc.sql(SELECT + """
                         WHERE (CAST(:assetId AS UUID) IS NULL OR r.asset_id = :assetId)
                           AND (CAST(:status AS VARCHAR) IS NULL OR r.status = :status)
                         ORDER BY r.created_at DESC
                         LIMIT :limit""")
                .param("assetId", assetId)
                .param("status", status == null ? null : status.name())
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    @Override
    public int failUnfinished(String errorCode, String errorMessage, Instant now) {
        return jdbc.sql("""
                        UPDATE enrichment_runs
                           SET status = 'FAILED', finished_at = :now, error_code = :code, error_message = :message
                         WHERE status IN ('QUEUED', 'RUNNING')""")
                .param("now", timestamptz(now))
                .param("code", errorCode)
                .param("message", errorMessage)
                .update();
    }

    private Map<String, Object> mutableParams(EnrichmentRun run) {
        Map<String, Object> params = new HashMap<>();
        params.put("status", run.status().name());
        params.put("startedAt", timestamptz(run.startedAt()));
        params.put("finishedAt", timestamptz(run.finishedAt()));
        params.put("stages", jsonMapper.writeValueAsString(run.stages()));
        params.put("summary", run.summary() == null ? null : jsonMapper.writeValueAsString(run.summary()));
        params.put("warnings", jsonMapper.writeValueAsString(run.warnings()));
        params.put("errorCode", run.errorCode());
        params.put("errorMessage", run.errorMessage());
        return params;
    }

    private EnrichmentRun map(ResultSet rs, int row) throws SQLException {
        String summary = rs.getString("summary");
        return new EnrichmentRun(
                uuid(rs, "id"),
                uuid(rs, "asset_id"),
                rs.getString("external_asset_id"),
                RunStatus.valueOf(rs.getString("status")),
                rs.getString("requested_by"),
                rs.getString("ai_model"),
                rs.getString("correlation_id"),
                instant(rs, "created_at"),
                instant(rs, "started_at"),
                instant(rs, "finished_at"),
                jsonMapper.readValue(rs.getString("stages"),
                        jsonMapper.getTypeFactory().constructCollectionType(List.class, StageState.class)),
                summary == null ? null : jsonMapper.readValue(summary, RunSummary.class),
                jsonMapper.readValue(rs.getString("warnings"),
                        jsonMapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                rs.getString("error_code"),
                rs.getString("error_message"));
    }
}

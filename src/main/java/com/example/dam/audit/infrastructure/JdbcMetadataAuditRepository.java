package com.example.dam.audit.infrastructure;

import static com.example.dam.common.persistence.SqlValues.confidence;
import static com.example.dam.common.persistence.SqlValues.instant;
import static com.example.dam.common.persistence.SqlValues.nullableDouble;
import static com.example.dam.common.persistence.SqlValues.timestamptz;
import static com.example.dam.common.persistence.SqlValues.uuid;

import com.example.dam.audit.domain.AuditQuery;
import com.example.dam.audit.domain.MetadataAuditRepository;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.audit.AuditAction;
import com.example.dam.metadata.domain.audit.MetadataAuditEvent;
import com.example.dam.metadata.infrastructure.persistence.MetadataValueJsonCodec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMetadataAuditRepository implements MetadataAuditRepository {

    private final JdbcClient jdbc;
    private final MetadataValueJsonCodec codec;

    JdbcMetadataAuditRepository(JdbcClient jdbc, MetadataValueJsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public void append(MetadataAuditEvent event) {
        jdbc.sql("""
                        INSERT INTO metadata_audit_events (id, asset_id, field_key, action, previous_value, new_value,
                                source, ai_model, confidence, actor, approved_by, proposal_id, reason, correlation_id,
                                occurred_at)
                        VALUES (:id, :assetId, :field, :action, CAST(:previousValue AS jsonb), CAST(:newValue AS jsonb),
                                :source, :aiModel, :confidence, :actor, :approvedBy, :proposalId, :reason,
                                :correlationId, :occurredAt)""")
                .param("id", event.id())
                .param("assetId", event.assetId())
                .param("field", event.field())
                .param("action", event.action().name())
                .param("previousValue", codec.toJson(event.previousValue()))
                .param("newValue", codec.toJson(event.newValue()))
                .param("source", event.source() == null ? null : event.source().name())
                .param("aiModel", event.aiModel())
                .param("confidence", confidence(event.confidence()))
                .param("actor", event.actor())
                .param("approvedBy", event.approvedBy())
                .param("proposalId", event.proposalId())
                .param("reason", event.reason())
                .param("correlationId", event.correlationId())
                .param("occurredAt", timestamptz(event.timestamp()))
                .update();
    }

    @Override
    public List<MetadataAuditEvent> findByAssetId(UUID assetId, int limit) {
        return findByAssetId(assetId, null, limit);
    }

    @Override
    public List<MetadataAuditEvent> findByAssetId(UUID assetId, String field, int limit) {
        return jdbc.sql("""
                        SELECT * FROM metadata_audit_events
                         WHERE asset_id = :assetId
                           AND (CAST(:field AS VARCHAR) IS NULL OR field_key = :field)
                         ORDER BY occurred_at DESC, id
                         LIMIT :limit""")
                .param("assetId", assetId)
                .param("field", field)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    @Override
    public Page search(AuditQuery query) {
        Map<String, Object> params = new HashMap<>();
        List<String> conditions = new ArrayList<>();
        condition(conditions, params, "e.asset_id = :assetId", "assetId", query.assetId());
        if (query.assetText() != null) {
            conditions.add("a.external_asset_id ILIKE :assetText");
            params.put("assetText", "%" + escapeLike(query.assetText()) + "%");
        }
        condition(conditions, params, "e.field_key = :field", "field", query.field());
        condition(conditions, params, "e.source = :source", "source", query.source() == null ? null : query.source().name());
        condition(conditions, params, "e.actor = :actor", "actor", query.actor());
        condition(conditions, params, "e.action = :action", "action", query.action() == null ? null : query.action().name());
        condition(conditions, params, "e.occurred_at >= :from", "from", timestamptz(query.from()));
        condition(conditions, params, "e.occurred_at < :to", "to", timestamptz(query.to()));
        if (query.actorType() != null) {
            conditions.add(switch (query.actorType()) {
                case AI -> "e.actor LIKE 'ai:%'";
                case SYSTEM -> "e.actor LIKE 'system:%'";
                case HUMAN -> "e.actor NOT LIKE 'ai:%' AND e.actor NOT LIKE 'system:%'";
            });
        }
        String from = " FROM metadata_audit_events e LEFT JOIN assets a ON a.id = e.asset_id"
                + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions));
        long total = jdbc.sql("SELECT count(*)" + from).params(params).query(Long.class).single();
        params.put("limit", query.size());
        params.put("offset", (long) query.page() * query.size());
        List<Entry> items = jdbc.sql("SELECT e.*, a.external_asset_id" + from
                        + " ORDER BY e.occurred_at DESC, e.id LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, row) -> new Entry(map(rs, row), rs.getString("external_asset_id")))
                .list();
        return new Page(items, query.page(), query.size(), total);
    }

    private static void condition(List<String> conditions, Map<String, Object> params, String sql, String name,
                                  Object value) {
        if (value != null) {
            conditions.add(sql);
            params.put(name, value);
        }
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private MetadataAuditEvent map(ResultSet rs, int row) throws SQLException {
        String field = rs.getString("field_key");
        String source = rs.getString("source");
        return new MetadataAuditEvent(
                uuid(rs, "id"),
                uuid(rs, "asset_id"),
                field,
                AuditAction.valueOf(rs.getString("action")),
                codec.fromJson(field, rs.getString("previous_value")),
                codec.fromJson(field, rs.getString("new_value")),
                source == null ? null : MetadataSource.valueOf(source),
                rs.getString("ai_model"),
                nullableDouble(rs, "confidence"),
                rs.getString("actor"),
                rs.getString("approved_by"),
                uuid(rs, "proposal_id"),
                rs.getString("reason"),
                rs.getString("correlation_id"),
                instant(rs, "occurred_at"));
    }
}

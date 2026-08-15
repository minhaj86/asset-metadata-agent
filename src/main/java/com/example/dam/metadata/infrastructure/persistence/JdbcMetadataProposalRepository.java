package com.example.dam.metadata.infrastructure.persistence;

import static com.example.dam.common.persistence.SqlValues.confidence;
import static com.example.dam.common.persistence.SqlValues.instant;
import static com.example.dam.common.persistence.SqlValues.timestamptz;
import static com.example.dam.common.persistence.SqlValues.uuid;

import com.example.dam.common.domain.ConflictException;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.MetadataStatus;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import com.example.dam.metadata.domain.proposal.MetadataProposal;
import com.example.dam.metadata.domain.proposal.MetadataProposalRepository;
import com.example.dam.metadata.domain.proposal.ProposalQuery;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMetadataProposalRepository implements MetadataProposalRepository {

    private final JdbcClient jdbc;
    private final MetadataValueJsonCodec codec;

    JdbcMetadataProposalRepository(JdbcClient jdbc, MetadataValueJsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public void insert(MetadataProposal proposal) {
        jdbc.sql("""
                        INSERT INTO metadata_proposals (id, asset_id, field_key, proposed_value, current_value, confidence,
                                explanation, evidence, source, proposed_by, ai_model, workflow_run_id, route, route_reason,
                                status, created_at, decided_at, decided_by, decision_comment, applied_value)
                        VALUES (:id, :assetId, :fieldKey, CAST(:proposedValue AS jsonb), CAST(:currentValue AS jsonb),
                                :confidence, :explanation, CAST(:evidence AS jsonb), :source, :proposedBy, :aiModel,
                                :workflowRunId, :route, :routeReason, :status, :createdAt, :decidedAt, :decidedBy,
                                :decisionComment, CAST(:appliedValue AS jsonb))""")
                .param("id", proposal.id())
                .param("assetId", proposal.assetId())
                .param("fieldKey", proposal.fieldKey())
                .param("proposedValue", codec.toJson(proposal.proposedValue()))
                .param("currentValue", codec.toJson(proposal.currentValue()))
                .param("confidence", confidence(proposal.confidence()))
                .param("explanation", proposal.explanation())
                .param("evidence", codec.stringListToJson(proposal.evidence()))
                .param("source", proposal.source().name())
                .param("proposedBy", proposal.proposedBy())
                .param("aiModel", proposal.aiModel())
                .param("workflowRunId", proposal.workflowRunId())
                .param("route", proposal.route().name())
                .param("routeReason", proposal.routeReason())
                .param("status", proposal.status().name())
                .param("createdAt", timestamptz(proposal.createdAt()))
                .param("decidedAt", timestamptz(proposal.decidedAt()))
                .param("decidedBy", proposal.decidedBy())
                .param("decisionComment", proposal.decisionComment())
                .param("appliedValue", codec.toJson(proposal.appliedValue()))
                .update();
    }

    @Override
    public void updateDecision(MetadataProposal proposal) {
        int updated = jdbc.sql("""
                        UPDATE metadata_proposals
                           SET status = :status, decided_at = :decidedAt, decided_by = :decidedBy,
                               decision_comment = :comment, applied_value = CAST(:appliedValue AS jsonb)
                         WHERE id = :id AND status = 'PROPOSED'""")
                .param("status", proposal.status().name())
                .param("decidedAt", timestamptz(proposal.decidedAt()))
                .param("decidedBy", proposal.decidedBy())
                .param("comment", proposal.decisionComment())
                .param("appliedValue", codec.toJson(proposal.appliedValue()))
                .param("id", proposal.id())
                .update();
        if (updated == 0) {
            throw new ConflictException("PROPOSAL_NOT_OPEN", "Proposal %s was decided concurrently".formatted(proposal.id()));
        }
    }

    @Override
    public Optional<MetadataProposal> findById(UUID id) {
        return jdbc.sql("SELECT * FROM metadata_proposals WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    public List<MetadataProposal> find(ProposalQuery query) {
        return jdbc.sql("""
                        SELECT * FROM metadata_proposals
                         WHERE (CAST(:status AS VARCHAR) IS NULL OR status = :status)
                           AND (CAST(:route AS VARCHAR) IS NULL OR route = :route)
                           AND (CAST(:assetId AS UUID) IS NULL OR asset_id = :assetId)
                           AND (CAST(:assetText AS VARCHAR) IS NULL OR asset_id IN (
                                   SELECT a.id FROM assets a WHERE a.external_asset_id ILIKE :assetText))
                           AND (CAST(:runId AS UUID) IS NULL OR workflow_run_id = :runId)
                           AND (CAST(:fieldKey AS VARCHAR) IS NULL OR field_key = :fieldKey)
                           AND (CAST(:proposedBy AS VARCHAR) IS NULL OR proposed_by = :proposedBy)
                           AND (CAST(:minConfidence AS NUMERIC) IS NULL OR confidence >= :minConfidence)
                           AND (CAST(:maxConfidence AS NUMERIC) IS NULL OR confidence < :maxConfidence)
                           AND (CAST(:createdAfter AS TIMESTAMPTZ) IS NULL OR created_at >= :createdAfter)
                         ORDER BY created_at DESC, field_key
                         LIMIT :limit""")
                .param("status", query.status() == null ? null : query.status().name())
                .param("route", query.route() == null ? null : query.route().name())
                .param("assetId", query.assetId())
                .param("assetText", query.assetText() == null ? null : "%" + escapeLike(query.assetText()) + "%")
                .param("runId", query.workflowRunId())
                .param("fieldKey", query.fieldKey())
                .param("proposedBy", query.proposedBy())
                .param("minConfidence", query.minConfidence() == null ? null : BigDecimal.valueOf(query.minConfidence()))
                .param("maxConfidence", query.maxConfidence() == null ? null : BigDecimal.valueOf(query.maxConfidence()))
                .param("createdAfter", timestamptz(query.createdAfter()))
                .param("limit", query.limit())
                .query(this::map)
                .list();
    }

    @Override
    public List<MetadataProposal> findOpenByAssetAndField(UUID assetId, String fieldKey) {
        return jdbc.sql("""
                        SELECT * FROM metadata_proposals
                         WHERE asset_id = :assetId AND field_key = :fieldKey AND status = 'PROPOSED'""")
                .param("assetId", assetId)
                .param("fieldKey", fieldKey)
                .query(this::map)
                .list();
    }

    @Override
    public long countOpenByAssetAndRoute(UUID assetId, ApprovalRoute route) {
        return jdbc.sql("""
                        SELECT count(*) FROM metadata_proposals
                         WHERE asset_id = :assetId AND route = :route AND status = 'PROPOSED'""")
                .param("assetId", assetId)
                .param("route", route.name())
                .query(Long.class)
                .single();
    }

    @Override
    public List<MetadataProposal> findLatestApprovedByAsset(UUID assetId) {
        return jdbc.sql("""
                        SELECT DISTINCT ON (field_key) * FROM metadata_proposals
                         WHERE asset_id = :assetId AND status = 'APPROVED'
                         ORDER BY field_key, decided_at DESC""")
                .param("assetId", assetId)
                .query(this::map)
                .list();
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private MetadataProposal map(ResultSet rs, int row) throws SQLException {
        String fieldKey = rs.getString("field_key");
        return new MetadataProposal(
                uuid(rs, "id"),
                uuid(rs, "asset_id"),
                fieldKey,
                codec.fromJson(fieldKey, rs.getString("proposed_value")),
                codec.fromJson(fieldKey, rs.getString("current_value")),
                rs.getBigDecimal("confidence").doubleValue(),
                rs.getString("explanation"),
                codec.stringListFromJson(rs.getString("evidence")),
                MetadataSource.valueOf(rs.getString("source")),
                rs.getString("proposed_by"),
                rs.getString("ai_model"),
                uuid(rs, "workflow_run_id"),
                ApprovalRoute.valueOf(rs.getString("route")),
                rs.getString("route_reason"),
                MetadataStatus.valueOf(rs.getString("status")),
                instant(rs, "created_at"),
                instant(rs, "decided_at"),
                rs.getString("decided_by"),
                rs.getString("decision_comment"),
                codec.fromJson(fieldKey, rs.getString("applied_value")));
    }
}

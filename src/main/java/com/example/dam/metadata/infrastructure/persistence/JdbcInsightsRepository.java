package com.example.dam.metadata.infrastructure.persistence;

import static com.example.dam.common.persistence.SqlValues.timestamptz;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.metadata.application.insights.InsightsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInsightsRepository implements InsightsRepository {

    private final JdbcClient jdbc;

    JdbcInsightsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AssetFigures assetFigures(LocalDate today, LocalDate within7Days, LocalDate withinWarningWindow) {
        Map<AssetStatus, Long> byStatus = new EnumMap<>(AssetStatus.class);
        jdbc.sql("SELECT status, count(*) AS assets FROM assets GROUP BY status")
                .query(rs -> {
                    byStatus.put(AssetStatus.valueOf(rs.getString("status")), rs.getLong("assets"));
                });
        return jdbc.sql("""
                        SELECT count(*) AS total,
                               count(*) FILTER (WHERE s.missing_required_count > 0) AS missing_required,
                               avg(s.quality_score) AS avg_quality,
                               avg(s.completeness) AS avg_completeness,
                               count(*) FILTER (WHERE s.completeness >= 95) AS c1,
                               count(*) FILTER (WHERE s.completeness >= 80 AND s.completeness < 95) AS c2,
                               count(*) FILTER (WHERE s.completeness >= 60 AND s.completeness < 80) AS c3,
                               count(*) FILTER (WHERE s.completeness < 60) AS c4,
                               count(*) FILTER (WHERE s.quality_score >= 90) AS q1,
                               count(*) FILTER (WHERE s.quality_score >= 70 AND s.quality_score < 90) AS q2,
                               count(*) FILTER (WHERE s.quality_score >= 50 AND s.quality_score < 70) AS q3,
                               count(*) FILTER (WHERE s.quality_score < 50) AS q4,
                               count(*) FILTER (WHERE s.asset_id IS NOT NULL AND s.usage_rights_end IS NULL)
                                   AS rights_missing,
                               count(*) FILTER (WHERE s.usage_rights_end < :today) AS rights_expired,
                               count(*) FILTER (WHERE s.usage_rights_end BETWEEN :today AND :within7) AS rights_7,
                               count(*) FILTER (WHERE s.usage_rights_end BETWEEN :today AND :withinWindow)
                                   AS rights_window
                          FROM assets a
                          LEFT JOIN asset_metadata_summaries s ON s.asset_id = a.id""")
                .param("today", Date.valueOf(today))
                .param("within7", Date.valueOf(within7Days))
                .param("withinWindow", Date.valueOf(withinWarningWindow))
                .query((rs, row) -> new AssetFigures(
                        rs.getLong("total"),
                        rs.getLong("missing_required"),
                        decimal(rs.getBigDecimal("avg_quality")),
                        decimal(rs.getBigDecimal("avg_completeness")),
                        byStatus,
                        List.of(rs.getLong("c1"), rs.getLong("c2"), rs.getLong("c3"), rs.getLong("c4")),
                        List.of(rs.getLong("q1"), rs.getLong("q2"), rs.getLong("q3"), rs.getLong("q4")),
                        rs.getLong("rights_missing"),
                        rs.getLong("rights_expired"),
                        rs.getLong("rights_7"),
                        rs.getLong("rights_window")))
                .single();
    }

    @Override
    public ProposalFigures proposalFigures() {
        return jdbc.sql("""
                        SELECT count(*) FILTER (WHERE status = 'PROPOSED' AND route = 'HUMAN_REVIEW') AS pending_review,
                               count(*) FILTER (WHERE status = 'PROPOSED' AND route = 'LOW_CONFIDENCE') AS pending_low,
                               count(*) FILTER (WHERE status = 'APPROVED' AND route = 'AUTO_APPROVE') AS auto_approved,
                               count(*) FILTER (WHERE status = 'APPROVED' AND route <> 'AUTO_APPROVE') AS human_approved,
                               count(*) FILTER (WHERE status = 'REJECTED' AND route = 'REJECTED_BY_POLICY')
                                   AS policy_rejected,
                               count(*) FILTER (WHERE status = 'REJECTED' AND route <> 'REJECTED_BY_POLICY'
                                                  AND decided_by LIKE 'system:%') AS superseded,
                               count(*) FILTER (WHERE status = 'REJECTED' AND route <> 'REJECTED_BY_POLICY'
                                                  AND decided_by NOT LIKE 'system:%') AS human_rejected
                          FROM metadata_proposals""")
                .query((rs, row) -> new ProposalFigures(rs.getLong("pending_review"), rs.getLong("pending_low"),
                        rs.getLong("auto_approved"), rs.getLong("human_approved"), rs.getLong("human_rejected"),
                        rs.getLong("policy_rejected"), rs.getLong("superseded")))
                .single();
    }

    @Override
    public RunFigures runFigures(Instant startOfToday) {
        return jdbc.sql("""
                        SELECT count(*) FILTER (WHERE created_at >= :startOfToday) AS today,
                               count(*) FILTER (WHERE status = 'FAILED' AND created_at >= :startOfToday) AS failed_today,
                               count(*) FILTER (WHERE status IN ('QUEUED', 'RUNNING')) AS active
                          FROM enrichment_runs""")
                .param("startOfToday", timestamptz(startOfToday))
                .query((rs, row) -> new RunFigures(rs.getLong("today"), rs.getLong("failed_today"),
                        rs.getLong("active")))
                .single();
    }

    private static Double decimal(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}

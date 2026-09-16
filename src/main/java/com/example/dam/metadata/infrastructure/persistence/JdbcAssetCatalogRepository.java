package com.example.dam.metadata.infrastructure.persistence;

import static com.example.dam.common.persistence.SqlValues.instant;
import static com.example.dam.common.persistence.SqlValues.uuid;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.catalog.AssetCatalogRepository;
import com.example.dam.metadata.application.catalog.CatalogItem;
import com.example.dam.metadata.application.catalog.CatalogItem.LastRun;
import com.example.dam.metadata.application.catalog.CatalogQuery;
import com.example.dam.metadata.application.catalog.CatalogQuery.AiStatus;
import com.example.dam.metadata.application.catalog.CatalogQuery.RightsStatus;
import com.example.dam.metadata.application.catalog.CatalogSort;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunStatus;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Catalog query: assets joined with their metadata summary, latest enrichment run and key metadata values. All
 * filters, sorting and paging happen in one SQL statement; derived states (AI status, rights status) are computed
 * from stored facts and the reference date passed in by the application.
 */
@Repository
class JdbcAssetCatalogRepository implements AssetCatalogRepository {

    private static final String CATALOG = """
            WITH catalog AS (
                SELECT a.id, a.external_asset_id, a.filename, a.mime_type, a.asset_url, a.asset_type, a.status,
                       a.created_at, a.updated_at,
                       s.asset_id, s.schema_version, s.valid, s.completeness, s.required_completeness,
                       s.quality_score, s.missing_required, s.missing_required_count, s.error_count, s.warning_count,
                       s.ai_value_count, s.pending_review_count, s.low_confidence_count, s.usage_rights_end,
                       s.refreshed_at,
                       lr.id AS last_run_id, lr.status AS last_run_status, lr.created_at AS last_run_created_at,
                       lr.finished_at AS last_run_finished_at,
                       km.key_metadata,
                       CASE WHEN lr.status IN ('QUEUED', 'RUNNING') THEN lr.status
                            WHEN COALESCE(s.pending_review_count, 0) > 0 THEN 'REVIEW_PENDING'
                            WHEN lr.status = 'FAILED' THEN 'FAILED'
                            WHEN COALESCE(s.ai_value_count, 0) > 0 OR lr.status = 'COMPLETED' THEN 'ENRICHED'
                            ELSE 'NOT_ENRICHED' END AS ai_status,
                       CASE WHEN s.usage_rights_end IS NULL THEN 'MISSING'
                            WHEN s.usage_rights_end < :today THEN 'EXPIRED'
                            WHEN s.usage_rights_end <= :expiringBefore THEN 'EXPIRING'
                            ELSE 'VALID' END AS rights_status
                  FROM assets a
                  LEFT JOIN asset_metadata_summaries s ON s.asset_id = a.id
                  LEFT JOIN LATERAL (SELECT r.id, r.status, r.created_at, r.finished_at
                                       FROM enrichment_runs r
                                      WHERE r.asset_id = a.id
                                      ORDER BY r.created_at DESC
                                      LIMIT 1) lr ON TRUE
                  LEFT JOIN LATERAL (SELECT jsonb_object_agg(m.field_key, m.value) AS key_metadata
                                       FROM asset_metadata m
                                      WHERE m.asset_id = a.id AND m.field_key IN (:keyFields)) km ON TRUE
            )
            """;

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    JdbcAssetCatalogRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public CatalogItem.Page search(CatalogQuery query, Context context) {
        Map<String, Object> params = baseParams(context);
        String where = where(query, context, params);
        long total = jdbc.sql(CATALOG + "SELECT count(*) FROM catalog c" + where)
                .params(params)
                .query(Long.class)
                .single();
        params.put("limit", query.size());
        params.put("offset", (long) query.page() * query.size());
        List<CatalogItem> items = jdbc.sql(CATALOG + "SELECT * FROM catalog c" + where
                        + " ORDER BY " + orderBy(query, params) + " LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, row) -> map(rs, context))
                .list();
        return new CatalogItem.Page(items, query.page(), query.size(), total);
    }

    @Override
    public Optional<CatalogItem> findById(UUID assetId, Context context) {
        Map<String, Object> params = baseParams(context);
        params.put("id", assetId);
        return jdbc.sql(CATALOG + "SELECT * FROM catalog c WHERE c.id = :id")
                .params(params)
                .query((rs, row) -> map(rs, context))
                .optional();
    }

    private static Map<String, Object> baseParams(Context context) {
        Map<String, Object> params = new HashMap<>();
        params.put("today", Date.valueOf(context.today()));
        params.put("expiringBefore", Date.valueOf(context.expiringBefore()));
        params.put("keyFields", context.keyFields().isEmpty() ? List.of("") : context.keyFields());
        return params;
    }

    private static String where(CatalogQuery query, Context context, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();
        if (query.text() != null) {
            conditions.add("""
                    (c.external_asset_id ILIKE :text OR c.filename ILIKE :text
                     OR EXISTS (SELECT 1 FROM asset_metadata t WHERE t.asset_id = c.id AND t.value::text ILIKE :text))""");
            params.put("text", "%" + escapeLike(query.text()) + "%");
        }
        condition(conditions, params, "c.asset_type = :type", "type", query.type(), AssetType::name);
        condition(conditions, params, "c.status = :status", "status", query.status(), AssetStatus::name);
        condition(conditions, params, "c.ai_status = :aiStatus", "aiStatus", query.aiStatus(), AiStatus::name);
        condition(conditions, params, "c.rights_status = :rightsStatus", "rightsStatus", query.rightsStatus(),
                RightsStatus::name);
        if (query.missingRequired() != null) {
            conditions.add(query.missingRequired() ? "c.missing_required_count > 0"
                    : "COALESCE(c.missing_required_count, 0) = 0");
        }
        condition(conditions, params, "c.missing_required @> jsonb_build_array(CAST(:missingField AS text))",
                "missingField", query.missingField(), value -> value);
        if (query.aiEnriched() != null) {
            conditions.add(query.aiEnriched() ? "c.ai_value_count > 0" : "COALESCE(c.ai_value_count, 0) = 0");
        }
        condition(conditions, params, "c.completeness >= :completenessMin", "completenessMin",
                query.completenessMin(), value -> value);
        condition(conditions, params, "c.completeness <= :completenessMax", "completenessMax",
                query.completenessMax(), value -> value);
        condition(conditions, params, "c.quality_score >= :qualityMin", "qualityMin", query.qualityMin(),
                value -> value);
        if (query.taxonomyPath() != null) {
            conditions.add("""
                    EXISTS (SELECT 1 FROM asset_metadata tx
                             WHERE tx.asset_id = c.id AND tx.field_key = :taxonomyField
                               AND (tx.value #>> '{}' = :taxonomyPath OR starts_with(tx.value #>> '{}', :taxonomyPrefix)))""");
            params.put("taxonomyField", context.taxonomyField());
            params.put("taxonomyPath", query.taxonomyPath());
            params.put("taxonomyPrefix", query.taxonomyPath() + " > ");
        }
        for (int i = 0; i < query.filters().size(); i++) {
            CatalogQuery.FieldFilter filter = query.filters().get(i);
            conditions.add("""
                    EXISTS (SELECT 1 FROM asset_metadata f%1$d
                             WHERE f%1$d.asset_id = c.id AND f%1$d.field_key = :filterField%1$d
                               AND CASE WHEN jsonb_typeof(f%1$d.value) = 'array'
                                        THEN EXISTS (SELECT 1 FROM jsonb_array_elements_text(f%1$d.value) e
                                                      WHERE lower(e) = lower(:filterValue%1$d))
                                        ELSE lower(f%1$d.value #>> '{}') = lower(:filterValue%1$d) END)"""
                    .formatted(i));
            params.put("filterField" + i, filter.field());
            params.put("filterValue" + i, filter.value());
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static <T> void condition(List<String> conditions, Map<String, Object> params, String sql, String name,
                                      T value, Function<T, Object> converter) {
        if (value != null) {
            conditions.add(sql);
            params.put(name, converter.apply(value));
        }
    }

    private static String orderBy(CatalogQuery query, Map<String, Object> params) {
        CatalogSort sort = query.sort();
        String column = switch (sort.column()) {
            case UPDATED_AT -> "c.updated_at";
            case CREATED_AT -> "c.created_at";
            case EXTERNAL_ASSET_ID -> "c.external_asset_id";
            case FILENAME -> "lower(c.filename)";
            case TYPE -> "c.asset_type";
            case STATUS -> "c.status";
            case COMPLETENESS -> "c.completeness";
            case QUALITY_SCORE -> "c.quality_score";
            case MISSING_REQUIRED -> "c.missing_required_count";
            case RIGHTS_END -> "c.usage_rights_end";
            case METADATA_FIELD -> {
                params.put("sortField", sort.metadataField());
                yield "lower(c.key_metadata ->> :sortField)";
            }
        };
        return column + (query.descending() ? " DESC" : " ASC") + " NULLS LAST, c.external_asset_id";
    }

    private CatalogItem map(ResultSet rs, Context context) throws SQLException {
        UUID lastRunId = uuid(rs, "last_run_id");
        return new CatalogItem(
                uuid(rs, "id"),
                rs.getString("external_asset_id"),
                rs.getString("filename"),
                rs.getString("mime_type"),
                rs.getString("asset_url"),
                AssetType.valueOf(rs.getString("asset_type")),
                AssetStatus.valueOf(rs.getString("status")),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                keyMetadata(rs.getString("key_metadata"), context.keyFields()),
                JdbcAssetSummaryRepository.map(rs, jsonMapper),
                AiStatus.valueOf(rs.getString("ai_status")),
                RightsStatus.valueOf(rs.getString("rights_status")),
                lastRunId == null ? null : new LastRun(lastRunId, RunStatus.valueOf(rs.getString("last_run_status")),
                        instant(rs, "last_run_created_at"), instant(rs, "last_run_finished_at")));
    }

    private Map<String, Object> keyMetadata(String json, List<String> keyFields) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        if (json == null) {
            return ordered;
        }
        Map<String, Object> values = jsonMapper.readValue(json,
                jsonMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        keyFields.stream().filter(values::containsKey).forEach(key -> ordered.put(key, values.get(key)));
        return ordered;
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

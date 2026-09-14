package com.example.dam.search.infrastructure;

import static com.example.dam.common.persistence.SqlValues.uuid;

import com.example.dam.search.application.AssetSearchCriteria.MetadataFilter;
import com.example.dam.search.application.port.AssetSearchRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Builds a single hybrid SQL query: EXISTS-subqueries for structured metadata filters, optional date window on the
 * usage-rights field and, when a query vector is present, pgvector cosine distance for ranking. Only the query
 * shape is dynamic; all field names and values are bound parameters.
 */
@Repository
class JdbcAssetSearchRepository implements AssetSearchRepository {

    /** Treats scalar and array JSONB values uniformly as a set of text elements. */
    private static final String ELEMENTS =
            "jsonb_array_elements_text(CASE WHEN jsonb_typeof(m.value) = 'array' THEN m.value ELSE jsonb_build_array(m.value) END)";
    private static final String ISO_DATE = "'^[0-9]{4}-[0-9]{2}-[0-9]{2}$'";

    private final JdbcClient jdbc;

    JdbcAssetSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SearchRow> search(SearchQuery query) {
        Map<String, Object> params = new LinkedHashMap<>();
        boolean semantic = query.queryVector() != null;
        StringBuilder sql = new StringBuilder();
        if (semantic) {
            params.put("queryVector", PgVectors.toLiteral(query.queryVector()));
            sql.append("""
                    SELECT a.id, 1 - (e.embedding <=> CAST(:queryVector AS vector)) AS similarity
                      FROM assets a
                      JOIN asset_embeddings e ON e.asset_id = a.id
                     WHERE TRUE""");
        } else {
            sql.append("SELECT a.id, CAST(NULL AS double precision) AS similarity FROM assets a WHERE TRUE");
        }
        if (query.assetType() != null) {
            sql.append(" AND a.asset_type = :assetType");
            params.put("assetType", query.assetType().name());
        }
        if (query.excludeAssetId() != null) {
            sql.append(" AND a.id <> :excludeAssetId");
            params.put("excludeAssetId", query.excludeAssetId());
        }
        int index = 0;
        for (MetadataFilter filter : query.filters()) {
            String key = "filterField" + index;
            String value = "filterValue" + index++;
            sql.append(" AND EXISTS (SELECT 1 FROM asset_metadata m WHERE m.asset_id = a.id AND m.field_key = :")
                    .append(key).append(" AND EXISTS (SELECT 1 FROM ").append(ELEMENTS)
                    .append(" AS v(element) WHERE lower(v.element) = lower(:").append(value).append(")))");
            params.put(key, filter.field());
            params.put(value, filter.value());
        }
        index = 0;
        for (String missing : query.missingFields()) {
            String key = "missingField" + index++;
            sql.append(" AND NOT EXISTS (SELECT 1 FROM asset_metadata m WHERE m.asset_id = a.id AND m.field_key = :")
                    .append(key).append(')');
            params.put(key, missing);
        }
        if (query.rightsExpiryField() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM asset_metadata m WHERE m.asset_id = a.id AND m.field_key = :rightsField")
                    .append(" AND (m.value #>> '{}') ~ ").append(ISO_DATE);
            if (query.rightsExpiryAfter() != null) {
                sql.append(" AND CAST(m.value #>> '{}' AS date) >= :rightsAfter");
                params.put("rightsAfter", query.rightsExpiryAfter());
            }
            if (query.rightsExpiryBefore() != null) {
                sql.append(" AND CAST(m.value #>> '{}' AS date) <= :rightsBefore");
                params.put("rightsBefore", query.rightsExpiryBefore());
            }
            sql.append(')');
            params.put("rightsField", query.rightsExpiryField());
        }
        if (semantic && query.minSimilarity() != null) {
            sql.append(" AND 1 - (e.embedding <=> CAST(:queryVector AS vector)) >= :minSimilarity");
            params.put("minSimilarity", query.minSimilarity());
        }
        sql.append(semantic
                ? " ORDER BY e.embedding <=> CAST(:queryVector AS vector), a.id"
                : " ORDER BY a.updated_at DESC, a.id");
        sql.append(" LIMIT :limit");
        params.put("limit", query.limit());

        return jdbc.sql(sql.toString())
                .params(params)
                .query((rs, row) -> new SearchRow(uuid(rs, "id"),
                        rs.getObject("similarity") == null ? null : rs.getDouble("similarity")))
                .list();
    }
}

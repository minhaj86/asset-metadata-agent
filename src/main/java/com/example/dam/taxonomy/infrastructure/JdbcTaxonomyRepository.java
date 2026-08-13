package com.example.dam.taxonomy.infrastructure;

import com.example.dam.taxonomy.domain.TaxonomyNode;
import com.example.dam.taxonomy.domain.TaxonomyRepository;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTaxonomyRepository implements TaxonomyRepository {

    private final JdbcClient jdbc;

    JdbcTaxonomyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<TaxonomyNode> findAll() {
        return jdbc.sql("""
                        SELECT n.id, n.parent_id, n.name, n.path, n.depth, n.synonyms, n.description,
                               NOT EXISTS (SELECT 1 FROM taxonomy_nodes c WHERE c.parent_id = n.id) AS leaf
                          FROM taxonomy_nodes n
                         ORDER BY n.path""")
                .query(this::map)
                .list();
    }

    private TaxonomyNode map(ResultSet rs, int row) throws SQLException {
        Array synonyms = rs.getArray("synonyms");
        return new TaxonomyNode(
                rs.getString("id"),
                rs.getString("parent_id"),
                rs.getString("name"),
                rs.getString("path"),
                rs.getInt("depth"),
                synonyms == null ? List.of() : List.of((String[]) synonyms.getArray()),
                rs.getString("description"),
                rs.getBoolean("leaf"));
    }
}

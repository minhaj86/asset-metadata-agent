package com.example.dam.ai.model.demo;

import static com.example.dam.ai.model.demo.DemoSupport.evidence;
import static com.example.dam.ai.model.demo.DemoSupport.humanize;
import static com.example.dam.ai.model.demo.DemoSupport.text;
import static com.example.dam.ai.model.demo.DemoSupport.value;

import com.example.dam.ai.agent.TaxonomyClassification;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;

/**
 * Demo rules for the TaxonomyAgent: retrieves valid nodes through the {@code searchTaxonomy} tool and picks the
 * best-ranked existing node; never constructs a path itself.
 */
final class TaxonomyResponder implements DemoResponder {

    private static final String TAXONOMY_TOOL = "searchTaxonomy";

    private final DemoSupport support;

    TaxonomyResponder(DemoSupport support) {
        this.support = support;
    }

    @Override
    public String schemaName() {
        return TaxonomyClassification.class.getSimpleName();
    }

    @Override
    public DemoReply reply(DemoConversation conversation) {
        JsonNode context = support.parse(conversation.section("asset_context").orElse(null));
        JsonNode working = support.parse(conversation.section("working_metadata").orElse(null));
        JsonNode product = context.path("product");
        String productType = Optional.ofNullable(value(working, "productType")).map(DemoSupport::humanize)
                .orElse(text(product, "productType"));
        String department = text(product, "department");
        String query = String.join(" ", Stream.of(productType, department)
                .filter(term -> term != null && !term.isBlank()).toList()).toLowerCase(Locale.ROOT);

        if (conversation.toolAvailable(TAXONOMY_TOOL) && conversation.lastToolResult(TAXONOMY_TOOL).isEmpty()
                && !query.isBlank()) {
            return DemoReply.callTool(TAXONOMY_TOOL, support.write(Map.of("query", query)));
        }
        JsonNode nodes = support.parse(conversation.lastToolResult(TAXONOMY_TOOL)
                .orElse(conversation.section("taxonomy_candidates").orElse("[]")));
        if (!nodes.isArray() || nodes.isEmpty()) {
            return DemoReply.answer(support.write(new TaxonomyClassification(null, null, 0.0,
                    "No existing taxonomy node matches the available evidence", List.of())));
        }
        JsonNode best = nodes.get(0);
        String path = text(best, "path");
        boolean departmentMatches = department != null && path != null
                && path.toLowerCase(Locale.ROOT).contains(department.toLowerCase(Locale.ROOT));
        double confidence = best.path("leaf").asBoolean(false) && departmentMatches ? 0.90 : 0.75;
        return DemoReply.answer(support.write(new TaxonomyClassification(text(best, "id"), path, confidence,
                "Best-ranked existing node for product type '%s'%s".formatted(humanize(productType),
                        department == null ? "" : " in department '" + department + "'"),
                evidence("searchTaxonomy(\"" + query + "\") top result: " + path,
                        department == null ? null : "product.department=" + department))));
    }
}

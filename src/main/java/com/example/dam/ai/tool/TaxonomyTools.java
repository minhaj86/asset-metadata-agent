package com.example.dam.ai.tool;

import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.taxonomy.application.TaxonomyService;
import com.example.dam.taxonomy.domain.TaxonomyNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import org.springframework.stereotype.Component;

/** Taxonomy retrieval tools: agents must pick nodes from these results instead of inventing paths. */
@Component
@ConditionalOnAiEnabled
public class TaxonomyTools {

    private static final int MAX_RESULTS = 8;

    private final TaxonomyService taxonomyService;

    public TaxonomyTools(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    public record TaxonomyNodeView(String id, String path, boolean leaf, List<String> synonyms) {
        static TaxonomyNodeView from(TaxonomyNode node) {
            return new TaxonomyNodeView(node.id(), node.path(), node.leaf(), node.synonyms());
        }
    }

    @Tool("Searches the product taxonomy and returns existing nodes ranked by relevance. Always use this before "
            + "choosing a taxonomy node; only ids returned here are valid.")
    public List<TaxonomyNodeView> searchTaxonomy(@P("Keywords such as product type and department, e.g. 'shirt men'")
                                                 String query) {
        return taxonomyService.search(query, MAX_RESULTS).stream().map(TaxonomyNodeView::from).toList();
    }

    @Tool("Returns one taxonomy node by id, or null if it does not exist")
    public TaxonomyNodeView getTaxonomyNode(@P("Taxonomy node id") String nodeId) {
        return taxonomyService.findById(nodeId.strip()).map(TaxonomyNodeView::from).orElse(null);
    }
}

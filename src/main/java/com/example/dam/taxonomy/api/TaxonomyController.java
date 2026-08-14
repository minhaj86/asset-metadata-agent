package com.example.dam.taxonomy.api;

import com.example.dam.taxonomy.application.TaxonomyService;
import com.example.dam.taxonomy.domain.TaxonomyNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/taxonomy")
@Validated
@Tag(name = "Taxonomy", description = "Hierarchical product taxonomy")
class TaxonomyController {

    private final TaxonomyService taxonomyService;

    TaxonomyController(TaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @GetMapping("/search")
    @Operation(summary = "Deterministic keyword search over node names, synonyms and ancestors")
    List<TaxonomyNodeResponse> search(@RequestParam @NotBlank String q,
                                      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return taxonomyService.search(q, limit).stream().map(TaxonomyNodeResponse::from).toList();
    }

    @GetMapping("/nodes")
    @Operation(summary = "All taxonomy nodes ordered by path")
    List<TaxonomyNodeResponse> nodes() {
        return taxonomyService.all().stream().map(TaxonomyNodeResponse::from).toList();
    }

    record TaxonomyNodeResponse(String id, String parentId, String name, String path, int depth, boolean leaf,
                                List<String> synonyms) {
        static TaxonomyNodeResponse from(TaxonomyNode node) {
            return new TaxonomyNodeResponse(node.id(), node.parentId(), node.name(), node.path(), node.depth(),
                    node.leaf(), node.synonyms());
        }
    }
}

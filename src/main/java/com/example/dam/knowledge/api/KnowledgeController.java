package com.example.dam.knowledge.api;

import com.example.dam.knowledge.application.KnowledgeService;
import com.example.dam.knowledge.application.port.KnowledgeAssistant.KnowledgeAnswer;
import com.example.dam.knowledge.application.port.KnowledgeBase.KnowledgeDocumentInfo;
import com.example.dam.knowledge.application.port.KnowledgeBase.KnowledgeSnippet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
@Validated
@Tag(name = "Knowledge", description = "RAG over metadata governance documentation")
class KnowledgeController {

    private final KnowledgeService knowledgeService;

    KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/search")
    @Operation(summary = "Semantic retrieval of governance knowledge (no LLM involved)")
    List<KnowledgeSnippet> search(@RequestParam @NotBlank String q,
                                  @RequestParam(defaultValue = "4") @Min(1) @Max(20) int limit) {
        return knowledgeService.search(q, limit);
    }

    @PostMapping("/ask")
    @Operation(summary = "Answer a governance question grounded in retrieved documentation")
    KnowledgeAnswer ask(@Valid @RequestBody AskRequest request) {
        return knowledgeService.ask(request.question());
    }

    @GetMapping("/documents")
    @Operation(summary = "Ingested knowledge documents")
    List<KnowledgeDocumentInfo> documents() {
        return knowledgeService.documents();
    }

    record AskRequest(@NotBlank @Size(max = 1000)
                      @Schema(example = "Which metadata fields are mandatory for PRODUCT_IMAGE?") String question) {
    }
}

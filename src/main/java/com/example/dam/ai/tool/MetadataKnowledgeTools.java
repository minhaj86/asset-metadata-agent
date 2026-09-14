package com.example.dam.ai.tool;

import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.ai.rag.MetadataKnowledgeService;
import com.example.dam.knowledge.application.port.KnowledgeBase.KnowledgeSnippet;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import org.springframework.stereotype.Component;

/** RAG as a tool: lets agents look up governance rules on demand instead of hard-coding them in prompts. */
@Component
@ConditionalOnAiEnabled
public class MetadataKnowledgeTools {

    private static final int MAX_RESULTS = 3;

    private final MetadataKnowledgeService knowledgeService;

    public MetadataKnowledgeTools(MetadataKnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    public record KnowledgeDocument(String title, String section, String text, double score) {
    }

    @Tool("Searches internal metadata governance documentation: field definitions, mandatory fields, naming "
            + "conventions, usage-rights rules, photography and alt-text guidelines")
    public List<KnowledgeDocument> searchMetadataDocumentation(@P("Question or keywords") String query) {
        List<KnowledgeSnippet> snippets = knowledgeService.search(query, MAX_RESULTS);
        return snippets.stream()
                .map(snippet -> new KnowledgeDocument(snippet.title(), snippet.section(), snippet.text(), snippet.score()))
                .toList();
    }
}

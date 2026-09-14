package com.example.dam.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.support.AbstractIntegrationTest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** RAG over the governance documentation: ingestion, pgvector retrieval and grounded answers. */
class KnowledgeRetrievalIT extends AbstractIntegrationTest {

    @Test
    void allKnowledgeDocumentsAreIngested() {
        JsonNode documents = get("/api/knowledge/documents");

        assertThat(documents.size()).isGreaterThanOrEqualTo(4);
        documents.values().forEach(document -> assertThat(document.get("chunks").asInt()).isPositive());
    }

    @Test
    void retrievesTheMandatoryFieldRuleForProductImages() {
        JsonNode top = search("Which metadata fields are mandatory for PRODUCT_IMAGE?").get(0);

        assertThat(top.get("documentId").asString()).isEqualTo("metadata-governance-rules");
        assertThat(top.get("section").asString()).isEqualTo("Mandatory fields for PRODUCT_IMAGE");
        assertThat(top.get("text").asString()).contains("productId", "copyrightOwner", "altText");
    }

    @Test
    void retrievesFieldDefinitionsAndPopulationRules() {
        assertThat(search("What does angleFacing mean?").get(0).get("section").asString()).isEqualTo("angleFacing");
        assertThat(search("When should intendedForABTest be populated?").get(0).get("section").asString())
                .isEqualTo("intendedForABTest and experimentIdentifier");
    }

    @Test
    void answersAreGroundedInRetrievedKnowledgeOrUnknown() {
        JsonNode answer = post("/api/knowledge/ask", Map.of("question", "What does angleFacing mean?"), USER, 200);
        assertThat(answer.get("grounded").asBoolean()).isTrue();
        assertThat(answer.get("answer").asString()).contains("FRONT");
        assertThat(answer.get("sources").get(0).get("section").asString()).isEqualTo("angleFacing");

        JsonNode offTopic = post("/api/knowledge/ask", Map.of("question", "What is the capital of France?"), USER, 200);
        assertThat(offTopic.get("grounded").asBoolean()).isFalse();
        assertThat(offTopic.get("answer").asString()).startsWith("UNKNOWN");
    }

    private JsonNode search(String query) {
        return get("/api/knowledge/search?limit=3&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }
}

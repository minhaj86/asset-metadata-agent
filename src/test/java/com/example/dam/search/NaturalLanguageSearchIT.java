package com.example.dam.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.ai.agent.SearchIntentOutput;
import com.example.dam.ai.agent.SearchIntentOutput.FilterCondition;
import com.example.dam.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Natural-language search: the SearchIntentAgent interprets, Java validates and executes deterministically. */
class NaturalLanguageSearchIT extends AbstractIntegrationTest {

    @Test
    void naturalLanguageQueryBecomesValidStructuredCriteria() {
        JsonNode response = semanticSearch("Find front-facing red dresses from summer campaign 2026.");

        assertThat(response.get("interpretedByAi").asBoolean()).isTrue();
        JsonNode criteria = response.get("interpretedCriteria");
        assertThat(criteria.get("filters").get("productType").asString()).isEqualTo("DRESS");
        assertThat(criteria.get("filters").get("color").asString()).isEqualTo("RED");
        assertThat(criteria.get("filters").get("angleFacing").asString()).isEqualTo("FRONT");
        assertThat(criteria.get("filters").get("campaign").asString()).isEqualTo("SUMMER_2026");
    }

    @Test
    void missingFieldAndAssetTypeQueriesAreUnderstood() {
        JsonNode criteria = semanticSearch("Show product images with missing copyright metadata").get("interpretedCriteria");

        assertThat(texts(criteria.get("missingFields"))).containsExactly("copyrightOwner");
        assertThat(criteria.get("assetType").asString()).isEqualTo("PRODUCT_IMAGE");
        assertThat(criteria.get("semanticQuery").isNull()).isTrue();
    }

    @Test
    void invalidInterpretationsAreDroppedAndDatesAreComputedByJava() {
        chatModel.respond(SearchIntentOutput.class, new SearchIntentOutput(
                List.of(new FilterCondition("color", "ultraviolet"), new FilterCondition("mood", "happy"),
                        new FilterCondition("productType", "shirts")),
                null, 30, List.of(), "PRODUCT_IMAGE", null, "scripted"));

        JsonNode response = semanticSearch("product images of shirts expiring within a month");

        JsonNode criteria = response.get("interpretedCriteria");
        assertThat(criteria.get("filters").size()).isZero();
        assertThat(criteria.get("rightsExpiryAfter").asString()).isEqualTo(LocalDate.now(ZoneOffset.UTC).toString());
        assertThat(criteria.get("rightsExpiryBefore").asString())
                .isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(30).toString());
        assertThat(texts(response.get("search").get("warnings"))).hasSize(3);
    }

    @Test
    void searchFallsBackToSemanticRankingWhenInterpreterIsUnavailable() {
        chatModel.fail(SearchIntentOutput.class);

        JsonNode response = semanticSearch("casual summer menswear");

        assertThat(response.get("interpretedByAi").asBoolean()).isFalse();
        assertThat(response.get("interpretedCriteria").get("semanticQuery").asString()).isEqualTo("casual summer menswear");
        assertThat(texts(response.get("search").get("warnings")))
                .anyMatch(warning -> warning.contains("interpretation unavailable"));
    }

    private JsonNode semanticSearch(String query) {
        return post("/api/assets/semantic-search", Map.of("query", query), USER, 200);
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.values().forEach(value -> values.add(value.asString()));
        return values;
    }
}

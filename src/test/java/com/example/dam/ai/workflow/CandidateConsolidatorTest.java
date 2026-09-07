package com.example.dam.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.ai.agent.EnrichmentOutput;
import com.example.dam.ai.agent.ExtractionOutput;
import com.example.dam.ai.agent.FieldSuggestion;
import com.example.dam.ai.agent.TaxonomyClassification;
import com.example.dam.ai.config.AiModelInfo;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.enrichment.AssetEnrichmentContext;
import com.example.dam.metadata.application.enrichment.MetadataCandidate;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.validation.MetadataValueParser;
import com.example.dam.metadata.domain.value.ListValue;
import com.example.dam.metadata.domain.value.TextValue;
import com.example.dam.support.InMemoryFakes;
import com.example.dam.support.MetadataFixtures;
import com.example.dam.taxonomy.application.TaxonomyService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The anti-corruption layer between untrusted LLM output and the domain. */
class CandidateConsolidatorTest {

    private final CandidateConsolidator consolidator = new CandidateConsolidator(new MetadataValueParser(),
            new TaxonomyService(InMemoryFakes::taxonomy), new AiModelInfo("demo", "test-model", "emb", true, null));
    private final AssetEnrichmentContext context = context();

    @Test
    void suggestionsAreTypedAndCanonicalised() {
        CandidateSet result = consolidate(List.of(
                suggestion("color", List.of("blue"), 0.95),
                suggestion("keywords", List.of("shirt", "oxford"), 0.94)), List.of(), null);

        assertThat(result.candidates()).extracting(MetadataCandidate::value)
                .containsExactly(new TextValue("BLUE"), ListValue.ofTexts(List.of("shirt", "oxford")));
        assertThat(result.candidates()).allSatisfy(candidate -> assertThat(candidate.aiModel()).isEqualTo("demo:test-model"));
    }

    @Test
    void unknownFieldsAndPlaceholderValuesAreDiscarded() {
        CandidateSet result = consolidate(List.of(
                suggestion("mood", List.of("happy"), 0.99),
                suggestion("brand", List.of("UNKNOWN"), 0.9),
                suggestion("title", List.of("", "  "), 0.9),
                suggestion("color", List.of("BLUE", "RED"), 0.9)), List.of(), null);

        assertThat(result.candidates()).isEmpty();
        assertThat(result.warnings()).hasSize(2);
    }

    @Test
    void inventedTaxonomyNodeIsRejected() {
        CandidateSet result = consolidate(List.of(), List.of(),
                new TaxonomyClassification("fashion.men.clothing.space-suits", "Fashion > Men > Clothing > Space Suits",
                        0.97, "Looks futuristic", List.of()));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.taxonomy().accepted()).isFalse();
        assertThat(result.taxonomy().rejectionReason()).contains("does not exist");
    }

    @Test
    void taxonomyPathIsTakenFromTheRepositoryNotFromTheModel() {
        CandidateSet result = consolidate(List.of(), List.of(),
                new TaxonomyClassification("fashion.men.clothing.shirts", "Men / Shirts", 0.9, "Shirt for men", List.of()));

        assertThat(result.taxonomy().accepted()).isTrue();
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.fieldKey()).isEqualTo("taxonomyPath");
            assertThat(candidate.value()).isEqualTo(new TextValue("Fashion > Men > Clothing > Shirts"));
            assertThat(candidate.evidence()).contains("taxonomyNodeId=fashion.men.clothing.shirts");
        });
    }

    @Test
    void duplicateSuggestionsKeepTheMostConfidentOne() {
        CandidateSet result = consolidate(
                List.of(suggestion("color", List.of("BLUE"), 0.80)),
                List.of(suggestion("color", List.of("BLUE"), 0.93)), null);

        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.confidence()).isEqualTo(0.93);
            assertThat(candidate.proposedBy()).isEqualTo(CandidateConsolidator.ENRICHMENT_AGENT);
        });
    }

    @Test
    void confidenceIsClampedAndTextSanitised() {
        CandidateSet result = consolidate(List.of(new FieldSuggestion("title", List.of("Oxford\u0000 Shirt"), 7.5,
                "because\nreasons", List.of("e1"))), List.of(), null);

        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.confidence()).isEqualTo(1.0);
            assertThat(candidate.value()).isEqualTo(new TextValue("Oxford Shirt"));
        });
    }

    private CandidateSet consolidate(List<FieldSuggestion> extraction, List<FieldSuggestion> enrichment,
                                     TaxonomyClassification taxonomy) {
        return consolidator.consolidate(context, new ExtractionOutput(extraction, "x"),
                new EnrichmentOutput(enrichment, "y"), taxonomy);
    }

    private static FieldSuggestion suggestion(String field, List<String> values, double confidence) {
        return new FieldSuggestion(field, values, confidence, "explanation", List.of("evidence"));
    }

    private static AssetEnrichmentContext context() {
        var asset = MetadataFixtures.asset(AssetType.PRODUCT_IMAGE, "blue-shirt-front.jpg");
        return new AssetEnrichmentContext(asset, MetadataFixtures.schema(AssetType.PRODUCT_IMAGE),
                AssetMetadataSnapshot.empty(asset.id()), Map.of(), null, null, List.of(), List.of());
    }
}

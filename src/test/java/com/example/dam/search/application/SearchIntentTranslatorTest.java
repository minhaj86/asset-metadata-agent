package com.example.dam.search.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.domain.schema.MetadataSchemaDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchemaRepository;
import com.example.dam.search.application.AssetSearchCriteria.MetadataFilter;
import com.example.dam.search.application.SearchIntentTranslator.Translation;
import com.example.dam.search.application.port.SearchIntentInterpreter.InterpretedSearchIntent;
import com.example.dam.search.application.port.SearchIntentInterpreter.SearchVocabulary;
import com.example.dam.support.InMemoryFakes.AssetStore;
import com.example.dam.support.MetadataFixtures;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** "LLM interprets, Java validates": the translator turns raw interpretations into safe, executable criteria. */
class SearchIntentTranslatorTest {

    private final AssetStore assets = new AssetStore();
    private final SearchIntentTranslator translator = new SearchIntentTranslator(
            new MetadataSchemaService(new FixedSchemaRepository()), assets,
            Clock.fixed(MetadataFixtures.NOW, ZoneOffset.UTC));

    @Test
    void validInterpretationBecomesStructuredCriteria() {
        Asset reference = assets.add(MetadataFixtures.asset(AssetType.PRODUCT_IMAGE, "blue-shirt-front.jpg"));
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("angleFacing", "front");
        filters.put("color", "Red");
        filters.put("productType", "DRESS");
        filters.put("campaign", "SUMMER_2026");

        Translation translation = translator.translate(new InterpretedSearchIntent(filters, "summer look", 30,
                List.of("copyrightOwner"), "product_image", reference.externalAssetId(), "test"), 10);

        AssetSearchCriteria criteria = translation.criteria();
        assertThat(translation.warnings()).isEmpty();
        assertThat(criteria.filters()).containsExactly(new MetadataFilter("angleFacing", "FRONT"),
                new MetadataFilter("color", "RED"), new MetadataFilter("productType", "DRESS"),
                new MetadataFilter("campaign", "SUMMER_2026"));
        assertThat(criteria.semanticQuery()).isEqualTo("summer look");
        assertThat(criteria.missingFields()).containsExactly("copyrightOwner");
        assertThat(criteria.assetType()).isEqualTo(AssetType.PRODUCT_IMAGE);
        assertThat(criteria.similarToAssetId()).isEqualTo(reference.id());
    }

    @Test
    void relativeRightsWindowIsComputedInJavaNotByTheModel() {
        AssetSearchCriteria criteria = translator.translate(new InterpretedSearchIntent(Map.of(), null, 30, List.of(),
                null, null, null), null).criteria();

        assertThat(criteria.rightsExpiryAfter()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(criteria.rightsExpiryBefore()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void hallucinatedFieldsValuesAndReferencesAreDroppedWithWarnings() {
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("mood", "happy");
        filters.put("color", "ultraviolet");
        filters.put("altText", "anything");

        Translation translation = translator.translate(new InterpretedSearchIntent(filters, null, -5,
                List.of("noSuchField"), "HOLOGRAM", "ABC123", null), null);

        assertThat(translation.criteria().filters()).isEmpty();
        assertThat(translation.criteria().missingFields()).isEmpty();
        assertThat(translation.criteria().assetType()).isNull();
        assertThat(translation.criteria().hasRightsWindow()).isFalse();
        assertThat(translation.criteria().similarToAssetId()).isNull();
        assertThat(translation.warnings()).hasSize(7);
    }

    @Test
    void vocabularyExposesOnlySearchableFields() {
        SearchVocabulary vocabulary = translator.vocabulary();

        assertThat(vocabulary.fields()).extracting(field -> field.key()).contains("color", "angleFacing", "campaign")
                .doesNotContain("altText", "description");
        assertThat(vocabulary.rightsExpiryField()).isEqualTo("usageRightsEnd");
    }

    private static final class FixedSchemaRepository implements MetadataSchemaRepository {
        @Override
        public Optional<MetadataSchemaDefinition> load() {
            return Optional.of(MetadataFixtures.definition());
        }

        @Override
        public void replace(MetadataSchemaDefinition definition) {
            throw new UnsupportedOperationException();
        }
    }
}

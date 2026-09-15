package com.example.dam.search.application;

import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.domain.schema.CrossFieldRule;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchemaDefinition;
import com.example.dam.search.application.AssetSearchCriteria.MetadataFilter;
import com.example.dam.search.application.port.SearchIntentInterpreter.InterpretedSearchIntent;
import com.example.dam.search.application.port.SearchIntentInterpreter.SearchVocabulary;
import com.example.dam.search.application.port.SearchIntentInterpreter.SearchableField;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Validates an LLM interpretation against the schema and turns it into executable criteria. Unknown fields,
 * invalid vocabulary values and unknown asset references are dropped with a warning; relative dates are computed
 * here with the application clock.
 */
@Component
public class SearchIntentTranslator {

    private static final int MAX_RIGHTS_WINDOW_DAYS = 3650;

    private final MetadataSchemaService schemaService;
    private final AssetRepository assetRepository;
    private final Clock clock;

    public SearchIntentTranslator(MetadataSchemaService schemaService, AssetRepository assetRepository, Clock clock) {
        this.schemaService = schemaService;
        this.assetRepository = assetRepository;
        this.clock = clock;
    }

    public record Translation(AssetSearchCriteria criteria, List<String> warnings) {
    }

    public SearchVocabulary vocabulary() {
        MetadataSchemaDefinition schema = schemaService.definition();
        List<SearchableField> fields = schema.fields().stream()
                .filter(MetadataFieldDefinition::searchable)
                .map(field -> new SearchableField(field.key(), field.displayName(), field.dataType().name(),
                        field.allowedValues(), field.description()))
                .toList();
        return new SearchVocabulary(fields, Arrays.stream(AssetType.values()).map(Enum::name).toList(),
                rightsExpiryField(schema).orElse(null));
    }

    public Translation translate(InterpretedSearchIntent intent, Integer limit) {
        MetadataSchemaDefinition schema = schemaService.definition();
        List<String> warnings = new ArrayList<>();

        List<MetadataFilter> filters = new ArrayList<>();
        intent.filters().forEach((key, rawValue) -> {
            Optional<MetadataFieldDefinition> field = schema.field(key).filter(MetadataFieldDefinition::searchable);
            if (field.isEmpty() || rawValue == null || rawValue.isBlank()) {
                warnings.add("Ignored filter on unknown or non-searchable field '%s'".formatted(key));
                return;
            }
            if (field.get().hasControlledVocabulary()) {
                field.get().canonicalAllowedValue(rawValue).ifPresentOrElse(
                        value -> filters.add(new MetadataFilter(key, value)),
                        () -> warnings.add("Ignored value '%s' for '%s' (not in vocabulary)".formatted(rawValue, key)));
            } else {
                filters.add(new MetadataFilter(key, rawValue.strip()));
            }
        });

        Set<String> missing = new LinkedHashSet<>();
        intent.missingFields().forEach(key -> {
            if (schema.field(key).isPresent()) {
                missing.add(key);
            } else {
                warnings.add("Ignored unknown missing-field '%s'".formatted(key));
            }
        });

        AssetType assetType = null;
        if (intent.assetType() != null && !intent.assetType().isBlank()) {
            try {
                assetType = AssetType.valueOf(intent.assetType().strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warnings.add("Ignored unknown asset type '%s'".formatted(intent.assetType()));
            }
        }

        LocalDate expiryAfter = null;
        LocalDate expiryBefore = null;
        Integer window = intent.rightsExpiringWithinDays();
        if (window != null) {
            if (window < 0 || window > MAX_RIGHTS_WINDOW_DAYS || rightsExpiryField(schema).isEmpty()) {
                warnings.add("Ignored rights expiry window of %d days".formatted(window));
            } else {
                expiryAfter = LocalDate.now(clock);
                expiryBefore = expiryAfter.plusDays(window);
            }
        }

        UUID similarTo = null;
        if (intent.similarToExternalAssetId() != null && !intent.similarToExternalAssetId().isBlank()) {
            similarTo = assetRepository.findByExternalAssetId(intent.similarToExternalAssetId().strip())
                    .map(asset -> asset.id())
                    .orElse(null);
            if (similarTo == null) {
                warnings.add("Reference asset '%s' not found; similarity ranking uses the text query"
                        .formatted(intent.similarToExternalAssetId()));
            }
        }
        return new Translation(new AssetSearchCriteria(filters, intent.semanticQuery(), expiryAfter, expiryBefore,
                missing, assetType, similarTo, limit), warnings);
    }

    private static Optional<String> rightsExpiryField(MetadataSchemaDefinition schema) {
        return schema.rules().stream()
                .filter(CrossFieldRule.RightsExpiry.class::isInstance)
                .map(rule -> ((CrossFieldRule.RightsExpiry) rule).field())
                .findFirst();
    }
}

package com.example.dam.search.application.port;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port for interpreting a natural-language query (implemented by the SearchIntentAgent). The interpreter only
 * understands intent; the resulting structure is validated and executed deterministically.
 */
public interface SearchIntentInterpreter {

    InterpretedSearchIntent interpret(String query, SearchVocabulary vocabulary);

    /** What the interpreter may refer to: searchable fields with their vocabularies, and asset types. */
    record SearchVocabulary(List<SearchableField> fields, List<String> assetTypes, String rightsExpiryField) {
        public SearchVocabulary {
            fields = List.copyOf(fields);
            assetTypes = List.copyOf(assetTypes);
        }
    }

    record SearchableField(String key, String displayName, String dataType, List<String> allowedValues,
                           String description) {
        public SearchableField {
            allowedValues = List.copyOf(allowedValues);
        }
    }

    /**
     * Raw interpretation; may contain unknown fields or invalid values which the translator drops.
     *
     * @param rightsExpiringWithinDays relative window; the date arithmetic is done in Java, not by the model
     */
    record InterpretedSearchIntent(Map<String, String> filters, String semanticQuery,
                                   Integer rightsExpiringWithinDays, List<String> missingFields, String assetType,
                                   String similarToExternalAssetId, String interpretationNotes) {
        public InterpretedSearchIntent {
            filters = filters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(filters));
            missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        }

        public static InterpretedSearchIntent semanticOnly(String query) {
            return new InterpretedSearchIntent(Map.of(), query, null, List.of(), null, null,
                    "Fallback: interpreter unavailable, using the query for semantic ranking only");
        }
    }
}

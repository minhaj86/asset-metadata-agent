package com.example.dam.search.application;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.search.application.AssetSearchService.AssetSearchResult;
import com.example.dam.search.application.SearchIntentTranslator.Translation;
import com.example.dam.search.application.port.SearchIntentInterpreter;
import com.example.dam.search.application.port.SearchIntentInterpreter.InterpretedSearchIntent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** "LLM interprets, Java executes": natural-language query -> validated criteria -> deterministic search. */
@Service
public class NaturalLanguageSearchService {

    private static final Logger log = LoggerFactory.getLogger(NaturalLanguageSearchService.class);

    private final ObjectProvider<SearchIntentInterpreter> interpreter;
    private final SearchIntentTranslator translator;
    private final AssetSearchService searchService;

    public NaturalLanguageSearchService(ObjectProvider<SearchIntentInterpreter> interpreter,
                                        SearchIntentTranslator translator, AssetSearchService searchService) {
        this.interpreter = interpreter;
        this.translator = translator;
        this.searchService = searchService;
    }

    /**
     * @param interpretedByAi {@code false} when the fallback (pure semantic search on the raw query) was used
     */
    public record NaturalLanguageSearchResult(String query, InterpretedSearchIntent interpretation,
                                              boolean interpretedByAi, AssetSearchResult result,
                                              List<String> warnings) {
    }

    /**
     * Structured constraints chosen explicitly by the user; they are merged into the interpreted criteria and win
     * over interpreted values for the same field.
     */
    public record Refinements(Map<String, String> filters, AssetType assetType, LocalDate rightsExpiryBefore) {
        public static final Refinements NONE = new Refinements(Map.of(), null, null);

        public Refinements {
            filters = filters == null ? Map.of() : Map.copyOf(filters);
        }

        boolean isEmpty() {
            return filters.isEmpty() && assetType == null && rightsExpiryBefore == null;
        }
    }

    public NaturalLanguageSearchResult search(String query, Integer limit) {
        return search(query, Refinements.NONE, limit);
    }

    public NaturalLanguageSearchResult search(String query, Refinements refinements, Integer limit) {
        List<String> warnings = new ArrayList<>();
        InterpretedSearchIntent intent;
        boolean interpretedByAi = false;
        SearchIntentInterpreter searchInterpreter = interpreter.getIfAvailable();
        if (searchInterpreter == null) {
            intent = InterpretedSearchIntent.semanticOnly(query);
            warnings.add("AI search interpretation is disabled; using semantic search only");
        } else {
            try {
                intent = searchInterpreter.interpret(query, translator.vocabulary());
                interpretedByAi = true;
            } catch (RuntimeException e) {
                log.warn("Search intent interpretation failed, falling back to semantic search: {}", e.getMessage());
                intent = InterpretedSearchIntent.semanticOnly(query);
                warnings.add("AI search interpretation unavailable; using semantic search only");
            }
        }
        Translation translation = translator.translate(intent, limit);
        warnings.addAll(translation.warnings());
        AssetSearchResult result = searchService.search(refine(translation.criteria(), refinements));
        warnings.addAll(result.warnings());
        return new NaturalLanguageSearchResult(query, intent, interpretedByAi, result, warnings);
    }

    private static AssetSearchCriteria refine(AssetSearchCriteria interpreted, Refinements refinements) {
        if (refinements == null || refinements.isEmpty()) {
            return interpreted;
        }
        Map<String, String> filters = new LinkedHashMap<>();
        interpreted.filters().forEach(filter -> filters.put(filter.field(), filter.value()));
        filters.putAll(refinements.filters());
        return new AssetSearchCriteria(
                filters.entrySet().stream()
                        .map(entry -> new AssetSearchCriteria.MetadataFilter(entry.getKey(), entry.getValue()))
                        .toList(),
                interpreted.semanticQuery(), interpreted.rightsExpiryAfter(),
                refinements.rightsExpiryBefore() != null ? refinements.rightsExpiryBefore() : interpreted.rightsExpiryBefore(),
                interpreted.missingFields(),
                refinements.assetType() != null ? refinements.assetType() : interpreted.assetType(),
                interpreted.similarToAssetId(), interpreted.limit());
    }
}

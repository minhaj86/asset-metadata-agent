package com.example.dam.ai.search;

import com.example.dam.ai.agent.AgentFactory;
import com.example.dam.ai.agent.PromptContextRenderer;
import com.example.dam.ai.agent.SearchIntentAgent;
import com.example.dam.ai.agent.SearchIntentOutput;
import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.ai.observability.AiCallObserver;
import com.example.dam.search.application.port.SearchIntentInterpreter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Implements the search-intent port with the {@link SearchIntentAgent}. Output is validated by the translator. */
@Component
@ConditionalOnAiEnabled
class LlmSearchIntentInterpreter implements SearchIntentInterpreter {

    private final SearchIntentAgent agent;
    private final AiCallObserver observer;
    private final PromptContextRenderer renderer;

    LlmSearchIntentInterpreter(AgentFactory agents, AiCallObserver observer, PromptContextRenderer renderer) {
        this.agent = agents.standaloneSearchIntentAgent();
        this.observer = observer;
        this.renderer = renderer;
    }

    @Override
    public InterpretedSearchIntent interpret(String query, SearchVocabulary vocabulary) {
        SearchIntentOutput output = observer.observe("searchIntentAgent", () -> agent.interpret(query,
                renderer.searchableFields(vocabulary), renderer.assetTypes(vocabulary)));
        Map<String, String> filters = new LinkedHashMap<>();
        if (output.filters() != null) {
            output.filters().stream()
                    .filter(Objects::nonNull)
                    .filter(filter -> filter.field() != null && filter.value() != null)
                    .forEach(filter -> filters.putIfAbsent(filter.field().strip(), filter.value().strip()));
        }
        return new InterpretedSearchIntent(filters, output.semanticQuery(), output.rightsExpiringWithinDays(),
                output.missingFields() == null ? List.of() : output.missingFields(), output.assetType(),
                output.similarToExternalAssetId(), output.interpretationNotes());
    }
}

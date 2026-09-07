package com.example.dam.ai.workflow;

import com.example.dam.ai.agent.EnrichmentOutput;
import com.example.dam.ai.agent.ExtractionOutput;
import com.example.dam.ai.agent.FieldSuggestion;
import com.example.dam.ai.agent.TaxonomyClassification;
import com.example.dam.ai.config.AiModelInfo;
import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.metadata.application.enrichment.AssetEnrichmentContext;
import com.example.dam.metadata.application.enrichment.MetadataCandidate;
import com.example.dam.metadata.application.enrichment.TaxonomySuggestion;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.WellKnownFields;
import com.example.dam.metadata.domain.validation.MetadataValueParser;
import com.example.dam.metadata.domain.validation.ParseResult;
import com.example.dam.metadata.domain.value.MetadataValue;
import com.example.dam.metadata.domain.value.TextValue;
import com.example.dam.taxonomy.application.TaxonomyService;
import com.example.dam.taxonomy.domain.TaxonomyNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Anti-corruption layer between untrusted LLM output and the domain: discards unknown fields and empty or
 * "UNKNOWN" values, sanitises and bounds text, types values with the schema, verifies taxonomy nodes against the
 * repository (taking the canonical path from the database, never from the model) and de-duplicates per field.
 * Invalid-but-representable values are kept so that the approval policy rejects them visibly and auditably.
 */
@Component
@ConditionalOnAiEnabled
public class CandidateConsolidator {

    public static final String EXTRACTION_AGENT = "metadataExtractionAgent";
    public static final String ENRICHMENT_AGENT = "metadataEnrichmentAgent";
    public static final String TAXONOMY_AGENT = "taxonomyAgent";

    private static final Set<String> NO_VALUE_MARKERS = Set.of("unknown", "n/a", "na", "none", "null", "-", "?",
            "not available", "not applicable");
    private static final int MAX_VALUE_LENGTH = 2000;
    private static final int MAX_EXPLANATION_LENGTH = 500;
    private static final int MAX_EVIDENCE_ITEMS = 10;
    private static final int MAX_EVIDENCE_LENGTH = 300;

    private final MetadataValueParser parser;
    private final TaxonomyService taxonomyService;
    private final AiModelInfo modelInfo;

    public CandidateConsolidator(MetadataValueParser parser, TaxonomyService taxonomyService, AiModelInfo modelInfo) {
        this.parser = parser;
        this.taxonomyService = taxonomyService;
        this.modelInfo = modelInfo;
    }

    public CandidateSet consolidate(AssetEnrichmentContext context, ExtractionOutput extraction,
                                    EnrichmentOutput enrichment, TaxonomyClassification taxonomy) {
        List<String> warnings = new ArrayList<>();
        List<MetadataCandidate> candidates = new ArrayList<>();
        candidates.addAll(fromSuggestions(context, extraction == null ? null : extraction.candidates(),
                EXTRACTION_AGENT, warnings));
        candidates.addAll(fromSuggestions(context, enrichment == null ? null : enrichment.proposals(),
                ENRICHMENT_AGENT, warnings));
        TaxonomySuggestion taxonomySuggestion = null;
        if (taxonomy != null) {
            taxonomySuggestion = taxonomy(taxonomy, candidates, warnings);
        }
        return new CandidateSet(mergeByField(candidates), taxonomySuggestion, warnings);
    }

    public List<MetadataCandidate> fromSuggestions(AssetEnrichmentContext context, List<FieldSuggestion> suggestions,
                                                   String agent, List<String> warnings) {
        if (suggestions == null) {
            return List.of();
        }
        List<MetadataCandidate> candidates = new ArrayList<>();
        for (FieldSuggestion suggestion : suggestions) {
            if (suggestion == null || suggestion.field() == null) {
                continue;
            }
            String key = suggestion.field().strip();
            Optional<MetadataFieldDefinition> field = context.schema().field(key);
            if (field.isEmpty()) {
                warnings.add("Discarded %s suggestion for unknown field '%s'".formatted(agent, key));
                continue;
            }
            List<String> values = suggestion.values() == null ? List.of() : suggestion.values().stream()
                    .map(CandidateConsolidator::sanitize)
                    .filter(value -> !value.isEmpty() && !NO_VALUE_MARKERS.contains(value.toLowerCase(Locale.ROOT)))
                    .toList();
            if (values.isEmpty()) {
                continue;
            }
            if (!field.get().repeatable() && values.size() > 1) {
                warnings.add("Discarded ambiguous %s suggestion with %d values for single-valued field '%s'"
                        .formatted(agent, values.size(), key));
                continue;
            }
            Object raw = field.get().repeatable() ? values : values.getFirst();
            ParseResult parsed = parser.parse(field.get(), raw);
            MetadataValue value = parsed.isSuccess() ? parsed.value() : parser.parseUntyped(raw);
            candidates.add(new MetadataCandidate(key, value, clamp(suggestion.confidence()),
                    bounded(sanitize(suggestion.explanation()), MAX_EXPLANATION_LENGTH), evidence(suggestion.evidence()),
                    agent, modelInfo.chatModelLabel()));
        }
        return candidates;
    }

    private TaxonomySuggestion taxonomy(TaxonomyClassification classification, List<MetadataCandidate> candidates,
                                        List<String> warnings) {
        String nodeId = classification.taxonomyNodeId() == null ? "" : classification.taxonomyNodeId().strip();
        double confidence = clamp(classification.confidence());
        String reason = bounded(sanitize(classification.reason()), MAX_EXPLANATION_LENGTH);
        if (nodeId.isEmpty()) {
            return new TaxonomySuggestion(null, null, confidence, reason, false, "No taxonomy node proposed");
        }
        Optional<TaxonomyNode> node = taxonomyService.findById(nodeId);
        if (node.isEmpty()) {
            String rejection = "Taxonomy node '%s' does not exist; invented taxonomy nodes are rejected".formatted(nodeId);
            warnings.add(rejection);
            return new TaxonomySuggestion(nodeId, classification.taxonomyPath(), confidence, reason, false, rejection);
        }
        List<String> evidence = new ArrayList<>(evidence(classification.evidence()));
        evidence.add("taxonomyNodeId=" + node.get().id());
        if (classification.taxonomyPath() != null && !node.get().path().equals(classification.taxonomyPath().strip())) {
            evidence.add("model-reported path replaced by the canonical path of the node");
        }
        candidates.add(new MetadataCandidate(WellKnownFields.TAXONOMY_PATH, new TextValue(node.get().path()), confidence,
                reason, evidence, TAXONOMY_AGENT, modelInfo.chatModelLabel()));
        return new TaxonomySuggestion(node.get().id(), node.get().path(), confidence, reason, true, null);
    }

    /** One candidate per field: the most confident wins; evidence of agreeing candidates is merged. */
    private static List<MetadataCandidate> mergeByField(List<MetadataCandidate> candidates) {
        Map<String, MetadataCandidate> byField = new LinkedHashMap<>();
        for (MetadataCandidate candidate : candidates) {
            byField.merge(candidate.fieldKey(), candidate, (existing, incoming) -> {
                MetadataCandidate winner = incoming.confidence() > existing.confidence() ? incoming : existing;
                MetadataCandidate other = winner == existing ? incoming : existing;
                if (!winner.value().isEquivalentTo(other.value())) {
                    return winner;
                }
                Set<String> evidence = new LinkedHashSet<>(winner.evidence());
                evidence.addAll(other.evidence());
                return new MetadataCandidate(winner.fieldKey(), winner.value(), winner.confidence(),
                        winner.explanation(), List.copyOf(evidence).subList(0, Math.min(evidence.size(), MAX_EVIDENCE_ITEMS)),
                        winner.proposedBy(), winner.aiModel());
            });
        }
        return List.copyOf(byField.values());
    }

    private static List<String> evidence(List<String> evidence) {
        if (evidence == null) {
            return List.of();
        }
        return evidence.stream()
                .map(CandidateConsolidator::sanitize)
                .filter(item -> !item.isEmpty())
                .map(item -> bounded(item, MAX_EVIDENCE_LENGTH))
                .limit(MAX_EVIDENCE_ITEMS)
                .toList();
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return bounded(text.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s{2,}", " ").strip(), MAX_VALUE_LENGTH);
    }

    private static String bounded(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static double clamp(double confidence) {
        return Double.isNaN(confidence) ? 0.0 : Math.clamp(confidence, 0.0, 1.0);
    }
}

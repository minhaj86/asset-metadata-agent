package com.example.dam.ai.model.demo;

import com.example.dam.ai.agent.FieldSuggestion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** JSON and vocabulary helpers shared by the demo responders. */
final class DemoSupport {

    private final JsonMapper json = JsonMapper.builder().build();

    JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return json.createObjectNode();
        }
        return json.readTree(text);
    }

    String write(Object value) {
        return json.writeValueAsString(value);
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asString().isBlank() ? null : value.asString();
    }

    static List<String> texts(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.values().forEach(item -> values.add(item.asString()));
            return values;
        }
        return value.isMissingNode() || value.isNull() ? List.of() : List.of(value.asString());
    }

    /** Value of a field in a {field: {value: ...}} map; first element for lists. */
    static String value(JsonNode fields, String field) {
        List<String> values = values(fields, field);
        return values.isEmpty() ? null : values.getFirst();
    }

    static List<String> values(JsonNode fields, String field) {
        return texts(fields.path(field), "value");
    }

    static String constant(String value) {
        return value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
    }

    static String humanize(String constant) {
        return constant == null ? null : constant.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    static String capitalize(String text) {
        return text == null || text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static String singular(String token) {
        if (token.length() <= 3) {
            return token;
        }
        if (token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.endsWith("sses") || token.endsWith("shes") || token.endsWith("ches") || token.endsWith("xes")) {
            return token.substring(0, token.length() - 2);
        }
        return token.endsWith("s") && !token.endsWith("ss") ? token.substring(0, token.length() - 1) : token;
    }

    static List<String> evidence(String... items) {
        return Arrays.stream(items).filter(Objects::nonNull).toList();
    }

    /** The field catalog from the prompt: which fields may be proposed and their vocabularies. */
    static final class FieldCatalog {

        private final Map<String, JsonNode> fields = new LinkedHashMap<>();

        FieldCatalog(JsonNode catalog) {
            catalog.values().forEach(field -> fields.put(field.path("key").asString(), field));
        }

        boolean has(String key) {
            return fields.containsKey(key);
        }

        boolean repeatable(String key) {
            return has(key) && fields.get(key).path("repeatable").asBoolean(false);
        }

        /** Canonical vocabulary value, the raw value for free-text fields, or empty if not allowed. */
        Optional<String> canonical(String key, String raw) {
            JsonNode allowed = fields.get(key).path("allowedValues");
            if (!allowed.isArray() || allowed.isEmpty()) {
                return Optional.of(raw.strip());
            }
            String wanted = constant(raw);
            return allowed.values().stream().map(JsonNode::asString).filter(value -> constant(value).equals(wanted))
                    .findFirst();
        }

        List<JsonNode> all() {
            return List.copyOf(fields.values());
        }
    }

    /** Collects suggestions the way a careful model would: catalog fields only, vocabulary-conformant, no no-ops. */
    static final class Suggestions {

        private final FieldCatalog catalog;
        private final JsonNode currentMetadata;
        private final List<FieldSuggestion> suggestions = new ArrayList<>();

        Suggestions(FieldCatalog catalog, JsonNode currentMetadata) {
            this.catalog = catalog;
            this.currentMetadata = currentMetadata;
        }

        void add(String field, List<String> rawValues, double confidence, String explanation, List<String> evidence) {
            if (!catalog.has(field) || rawValues.isEmpty()) {
                return;
            }
            List<String> values = new ArrayList<>();
            for (String raw : rawValues) {
                Optional<String> canonical = catalog.canonical(field, raw);
                if (canonical.isEmpty()) {
                    return;
                }
                if (!values.contains(canonical.get())) {
                    values.add(canonical.get());
                }
            }
            List<String> finalValues = catalog.repeatable(field) ? values : List.of(values.getFirst());
            Set<String> current = values(currentMetadata, field).stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            Set<String> proposed = finalValues.stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            if (current.containsAll(proposed)) {
                return;
            }
            suggestions.add(new FieldSuggestion(field, finalValues, confidence, explanation, evidence));
        }

        void add(String field, String rawValue, double confidence, String explanation, List<String> evidence) {
            if (rawValue != null && !rawValue.isBlank()) {
                add(field, List.of(rawValue), confidence, explanation, evidence);
            }
        }

        List<FieldSuggestion> list() {
            return List.copyOf(suggestions);
        }

        int size() {
            return suggestions.size();
        }
    }
}

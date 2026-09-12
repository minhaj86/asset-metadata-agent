package com.example.dam.ai.model.demo;

import static com.example.dam.ai.model.demo.DemoSupport.constant;
import static com.example.dam.ai.model.demo.DemoSupport.singular;
import static com.example.dam.ai.model.demo.DemoSupport.texts;

import com.example.dam.ai.agent.SearchIntentOutput;
import com.example.dam.ai.agent.SearchIntentOutput.FilterCondition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Demo rules for the SearchIntentAgent: maps phrases to the supplied vocabulary, recognises rights windows,
 * missing fields, campaign codes, asset types and "similar to" references. Date arithmetic is left to Java.
 */
final class SearchIntentResponder implements DemoResponder {

    private static final Pattern SIMILAR = Pattern.compile("similar to (?:asset )?([a-z0-9][a-z0-9-]*)");
    private static final Pattern EXPIRY = Pattern.compile(
            "(?:expire|expires|expiring|end|ends)\\s+(?:with)?in\\s+(?:the\\s+next\\s+)?(\\d+)\\s+days?");
    private static final Pattern MISSING = Pattern.compile(
            "(?:missing|without|lacking)\\s+([a-z ]+?)\\s+(?:metadata|information|data|fields?|values?)");
    private static final Pattern CAMPAIGN = Pattern.compile("(summer|winter|spring|autumn|fall)\\s+(?:campaign\\s+)?(\\d{4})");
    private static final Map<Pattern, String> ASSET_TYPES = Map.of(
            Pattern.compile("\\bproduct (?:images?|photos?|shots?)\\b"), "PRODUCT_IMAGE",
            Pattern.compile("\\bcampaign (?:images?|photos?)\\b"), "CAMPAIGN_IMAGE",
            Pattern.compile("\\b(?:model|on-model) (?:images?|photos?)\\b"), "MODEL_IMAGE",
            Pattern.compile("\\bvideos?\\b"), "VIDEO",
            Pattern.compile("\\b(?:documents?|pdfs?)\\b"), "DOCUMENT",
            Pattern.compile("\\bdigital twins?\\b"), "DIGITAL_TWIN");
    private static final Set<String> FILLER = Set.of("find", "show", "list", "get", "give", "me", "all", "any", "the",
            "a", "an", "of", "for", "with", "from", "in", "on", "and", "or", "to", "that", "which", "whose", "are", "is",
            "asset", "assets", "image", "images", "photo", "photos", "picture", "pictures", "facing", "please",
            "metadata", "usage", "rights", "campaign", "days", "day", "within");

    private final DemoSupport support;

    SearchIntentResponder(DemoSupport support) {
        this.support = support;
    }

    @Override
    public String schemaName() {
        return SearchIntentOutput.class.getSimpleName();
    }

    @Override
    public DemoReply reply(DemoConversation conversation) {
        String query = conversation.section("query").orElse("").strip();
        JsonNode fields = support.parse(conversation.section("searchable_fields").orElse("[]"));
        List<String> assetTypes = texts(support.parse("{\"t\":" + conversation.section("asset_types").orElse("[]") + "}"), "t");
        String remaining = " " + query.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("[.,!?;:]", " ") + " ";
        List<String> notes = new ArrayList<>();

        String similarTo = null;
        Matcher matcher = SIMILAR.matcher(remaining);
        if (matcher.find()) {
            similarTo = matcher.group(1).toUpperCase(Locale.ROOT);
            remaining = remove(remaining, matcher);
            notes.add("similar to " + similarTo);
        }
        Integer days = null;
        matcher = EXPIRY.matcher(remaining);
        if (matcher.find()) {
            days = Integer.parseInt(matcher.group(1));
            remaining = remove(remaining, matcher);
            notes.add("rights expiring within " + days + " days");
        }
        List<String> missing = new ArrayList<>();
        matcher = MISSING.matcher(remaining);
        if (matcher.find()) {
            String term = matcher.group(1).strip();
            fieldMatching(fields, term).ifPresent(field -> {
                missing.add(field);
                notes.add("missing " + field);
            });
            remaining = remove(remaining, matcher);
        }
        String assetType = null;
        for (Map.Entry<Pattern, String> entry : ASSET_TYPES.entrySet()) {
            Matcher typeMatcher = entry.getKey().matcher(remaining);
            if (assetTypes.contains(entry.getValue()) && typeMatcher.find()) {
                assetType = entry.getValue();
                remaining = remove(remaining, typeMatcher);
                notes.add("asset type " + assetType);
                break;
            }
        }
        Map<String, String> filters = new LinkedHashMap<>();
        matcher = CAMPAIGN.matcher(remaining);
        String campaignField = keyContaining(fields, "campaign");
        if (campaignField != null && matcher.find()) {
            String season = matcher.group(1).equals("fall") ? "AUTUMN" : matcher.group(1).toUpperCase(Locale.ROOT);
            filters.put(campaignField, season + "_" + matcher.group(2));
            remaining = remove(remaining, matcher);
        }

        List<String> tokens = new ArrayList<>(List.of(remaining.strip().split("[^a-z0-9']+")));
        boolean descriptiveLeft = false;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.isBlank() || FILLER.contains(token)) {
                continue;
            }
            if (i + 1 < tokens.size() && vocabularyMatch(fields, token + " " + tokens.get(i + 1), filters)) {
                i++;
                continue;
            }
            // Singular first: "dresses" means productType DRESS rather than category DRESSES.
            if (vocabularyMatch(fields, singular(token), filters) || vocabularyMatch(fields, token, filters)) {
                continue;
            }
            descriptiveLeft = true;
        }
        filters.forEach((field, value) -> notes.add(field + "=" + value));
        String semanticQuery = descriptiveLeft ? query.replaceAll("[.!?]+$", "") : null;
        List<FilterCondition> conditions = filters.entrySet().stream()
                .map(entry -> new FilterCondition(entry.getKey(), entry.getValue())).toList();
        return DemoReply.answer(support.write(new SearchIntentOutput(conditions, semanticQuery, days, missing,
                assetType, similarTo, "Offline demo interpretation: " + (notes.isEmpty() ? "free text only"
                : String.join(", ", notes)))));
    }

    /** Assigns the token to the first field whose vocabulary contains it (fields earlier in the list win). */
    private static boolean vocabularyMatch(JsonNode fields, String phrase, Map<String, String> filters) {
        String wanted = constant(phrase);
        if (wanted.isEmpty()) {
            return false;
        }
        for (JsonNode field : fields.values()) {
            String key = field.path("key").asString();
            for (JsonNode allowed : field.path("allowedValues").values()) {
                if (constant(allowed.asString()).equals(wanted)) {
                    if (!filters.containsKey(key) && !filters.containsValue(allowed.asString())) {
                        filters.put(key, allowed.asString());
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static Optional<String> fieldMatching(JsonNode fields, String term) {
        String compact = term.replace(" ", "").toLowerCase(Locale.ROOT);
        for (JsonNode field : fields.values()) {
            String key = field.path("key").asString().toLowerCase(Locale.ROOT);
            String display = field.path("displayName").asString().replace(" ", "").toLowerCase(Locale.ROOT);
            if (key.contains(compact) || display.contains(compact)) {
                return Optional.of(field.path("key").asString());
            }
        }
        return Optional.empty();
    }

    private static String keyContaining(JsonNode fields, String fragment) {
        for (JsonNode field : fields.values()) {
            String key = field.path("key").asString();
            if (key.toLowerCase(Locale.ROOT).contains(fragment)) {
                return key;
            }
        }
        return null;
    }

    private static String remove(String text, Matcher matcher) {
        return text.substring(0, matcher.start()) + " " + text.substring(matcher.end());
    }
}

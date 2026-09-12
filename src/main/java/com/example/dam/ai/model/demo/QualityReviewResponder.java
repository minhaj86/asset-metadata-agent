package com.example.dam.ai.model.demo;

import static com.example.dam.ai.model.demo.DemoSupport.humanize;
import static com.example.dam.ai.model.demo.DemoSupport.singular;
import static com.example.dam.ai.model.demo.DemoSupport.value;
import static com.example.dam.ai.model.demo.DemoSupport.values;

import com.example.dam.ai.agent.QualityReviewOutput;
import com.example.dam.ai.agent.QualityReviewOutput.QualityFinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Demo rules for the MetadataQualityAgent: soft, semantic checks that deterministic validation cannot express. */
final class QualityReviewResponder implements DemoResponder {

    private static final Set<String> REDUNDANT_ALT_PREFIXES = Set.of("image of", "picture of", "photo of", "an image");
    private static final Set<String> GENERIC_KEYWORDS =
            Set.of("image", "photo", "picture", "nice", "best", "new", "sale", "product", "item", "stuff");
    private static final Set<String> COLOR_WORDS = Set.of("black", "white", "grey", "gray", "beige", "brown", "navy",
            "blue", "green", "red", "pink", "yellow", "orange", "purple");

    private final DemoSupport support;

    QualityReviewResponder(DemoSupport support) {
        this.support = support;
    }

    @Override
    public String schemaName() {
        return QualityReviewOutput.class.getSimpleName();
    }

    @Override
    public DemoReply reply(DemoConversation conversation) {
        JsonNode fields = support.parse(conversation.section("metadata_to_review").orElse(null)).path("fields");
        List<QualityFinding> findings = new ArrayList<>();

        String altText = value(fields, "altText");
        if (altText != null) {
            String lower = altText.toLowerCase(Locale.ROOT);
            if (REDUNDANT_ALT_PREFIXES.stream().anyMatch(lower::startsWith)) {
                findings.add(new QualityFinding("altText", "POOR_ALT_TEXT", "MEDIUM",
                        "Alt text starts with a redundant phrase that screen readers already announce",
                        "Start with what is shown, e.g. 'Front view of ...'"));
            }
            if (altText.length() < 25) {
                findings.add(new QualityFinding("altText", "POOR_ALT_TEXT", "HIGH",
                        "Alt text is too short to describe the image", "Describe view, product, colour and background"));
            }
        }
        String description = value(fields, "description");
        if (description != null && description.length() < 40) {
            findings.add(new QualityFinding("description", "WEAK_DESCRIPTION", "MEDIUM",
                    "Description is too short to be useful for search or customers",
                    "Describe the product, material, colour and the photographic set-up"));
        }
        List<String> keywords = values(fields, "keywords").stream().map(k -> k.toLowerCase(Locale.ROOT)).toList();
        List<String> generic = keywords.stream().filter(GENERIC_KEYWORDS::contains).toList();
        if (!generic.isEmpty()) {
            findings.add(new QualityFinding("keywords", "IRRELEVANT_KEYWORDS", "LOW",
                    "Generic keywords add no search value: " + generic, "Remove them or replace them with specific terms"));
        }
        String color = humanize(value(fields, "color"));
        if (color != null) {
            List<String> conflicting = keywords.stream()
                    .filter(COLOR_WORDS::contains)
                    .filter(keyword -> !color.contains(keyword))
                    .toList();
            if (!conflicting.isEmpty()) {
                findings.add(new QualityFinding("keywords", "INCONSISTENT_SEMANTICS", "MEDIUM",
                        "Keywords %s contradict the colour '%s'".formatted(conflicting, color),
                        "Align keywords with the colour field"));
            }
        }
        String taxonomyPath = value(fields, "taxonomyPath");
        String productType = humanize(value(fields, "productType"));
        if (taxonomyPath != null && productType != null) {
            String leaf = taxonomyPath.substring(taxonomyPath.lastIndexOf('>') + 1).strip().toLowerCase(Locale.ROOT);
            String type = singular(productType.replace(' ', '-'));
            if (!leaf.contains(type) && !singular(leaf).contains(type)) {
                findings.add(new QualityFinding("taxonomyPath", "AMBIGUOUS_CLASSIFICATION", "MEDIUM",
                        "Taxonomy node '%s' does not obviously match product type '%s'".formatted(leaf, productType),
                        "Verify the taxonomy classification"));
            }
        }
        String summary = findings.isEmpty()
                ? "No semantic quality issues found"
                : "%d semantic quality issue(s) found".formatted(findings.size());
        return DemoReply.answer(support.write(new QualityReviewOutput(findings, 0.80, summary)));
    }
}

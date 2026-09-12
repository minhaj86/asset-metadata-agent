package com.example.dam.ai.model.demo;

import static com.example.dam.ai.model.demo.DemoSupport.capitalize;
import static com.example.dam.ai.model.demo.DemoSupport.evidence;
import static com.example.dam.ai.model.demo.DemoSupport.humanize;
import static com.example.dam.ai.model.demo.DemoSupport.text;
import static com.example.dam.ai.model.demo.DemoSupport.texts;
import static com.example.dam.ai.model.demo.DemoSupport.value;

import com.example.dam.ai.agent.EnrichmentOutput;
import com.example.dam.ai.model.demo.DemoSupport.FieldCatalog;
import com.example.dam.ai.model.demo.DemoSupport.Suggestions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/**
 * Demo rules for the MetadataEnrichmentAgent. First consults the governance documentation through the
 * {@code searchMetadataDocumentation} tool (RAG as a tool), then composes descriptive metadata from product data
 * and the working metadata. Without product evidence it does not describe the image (never invent).
 */
final class EnrichmentResponder implements DemoResponder {

    private static final String KNOWLEDGE_TOOL = "searchMetadataDocumentation";
    private static final Set<String> FIT_WORDS = Set.of("regular", "slim", "relaxed", "loose", "oversized", "fit");
    private static final Set<String> PROPER_NOUNS = Set.of("oxford", "chelsea", "breton");
    private static final Map<String, String> CATEGORY_BY_TYPE = Map.ofEntries(
            Map.entry("SHIRT", "TOPS"), Map.entry("T_SHIRT", "TOPS"), Map.entry("TOP", "TOPS"),
            Map.entry("TROUSERS", "BOTTOMS"), Map.entry("JEANS", "BOTTOMS"), Map.entry("SKIRT", "BOTTOMS"),
            Map.entry("DRESS", "DRESSES"), Map.entry("JACKET", "OUTERWEAR"), Map.entry("KNITWEAR", "KNITWEAR"),
            Map.entry("SNEAKERS", "FOOTWEAR"), Map.entry("BOOTS", "FOOTWEAR"), Map.entry("BAG", "ACCESSORIES"),
            Map.entry("ACCESSORY", "ACCESSORIES"), Map.entry("CUSHION", "HOME"));

    private final DemoSupport support;

    EnrichmentResponder(DemoSupport support) {
        this.support = support;
    }

    @Override
    public String schemaName() {
        return EnrichmentOutput.class.getSimpleName();
    }

    @Override
    public DemoReply reply(DemoConversation conversation) {
        JsonNode context = support.parse(conversation.section("asset_context").orElse(null));
        String assetType = text(context.path("asset"), "assetType");
        if (conversation.toolAvailable(KNOWLEDGE_TOOL) && conversation.lastToolResult(KNOWLEDGE_TOOL).isEmpty()) {
            return DemoReply.callTool(KNOWLEDGE_TOOL, support.write(Map.of("query",
                    "Alt text, title and keyword guidelines for " + assetType)));
        }
        JsonNode working = support.parse(conversation.section("working_metadata").orElse(null));
        FieldCatalog catalog = new FieldCatalog(support.parse(conversation.section("field_catalog").orElse("[]")));
        Suggestions out = new Suggestions(catalog, working);
        String guideline = guideline(conversation).orElse(null);

        JsonNode product = context.path("product");
        JsonNode analysis = context.path("imageAnalysis");
        String productName = text(product, "name");
        String department = text(product, "department");
        String angle = Optional.ofNullable(value(working, "angleFacing")).orElse(text(analysis, "orientation"));
        String background = Optional.ofNullable(value(working, "backgroundType")).orElse(text(analysis, "background"));
        String productType = Optional.ofNullable(value(working, "productType")).orElse(text(product, "productType"));
        String colorText = Optional.ofNullable(text(product, "color")).orElse(humanize(value(working, "color")));
        String shotType = value(working, "shotType");
        List<String> materials = texts(product, "materials").stream().map(m -> m.toLowerCase(Locale.ROOT)).toList();

        if (productName != null) {
            List<String> basis = evidence("product.name=" + productName, angle == null ? null : "angleFacing=" + angle,
                    guideline == null ? null : "guideline: " + guideline);
            String view = angle == null ? null : capitalize(angle.toLowerCase(Locale.ROOT)) + " View";
            out.add("title", view == null ? productName : productName + " - " + view, 0.90,
                    "Product name from the product information system combined with the view", basis);
            if (angle != null && colorText != null && background != null) {
                String altText = "%s view of a %s %s%s on a %s background".formatted(
                        capitalize(angle.toLowerCase(Locale.ROOT)), colorText.toLowerCase(Locale.ROOT),
                        possessive(department), nounPhrase(productName), background.toLowerCase(Locale.ROOT));
                out.add("altText", altText, 0.88,
                        "Describes view, colour, product and background as required by the alt-text guideline", basis);
            }
            out.add("keywords", keywords(productType, productName, colorText, department, materials), 0.94,
                    "Search keywords from product type, product name, colour, department and material", basis);
            if (shotType != null && colorText != null && angle != null) {
                out.add("description", "%s image of the %s in %s%s, shown from the %s.".formatted(
                                capitalize(humanize(shotType)), productName, colorText.toLowerCase(Locale.ROOT),
                                materials.isEmpty() ? "" : " " + String.join(" and ", materials),
                                angle.toLowerCase(Locale.ROOT)),
                        0.93, "Factual description assembled from product data and image metadata", basis);
            }
            out.add("tags", tags(productType, department, colorText, angle), 0.93,
                    "Normalised search tags derived from existing metadata", basis);
        } else if (value(working, "title") == null) {
            String filename = text(context.path("asset"), "filename");
            if (filename != null) {
                out.add("title", capitalize(filename.replaceFirst("\\.[a-zA-Z0-9]+$", "").replace('-', ' ')), 0.55,
                        "Only the filename is available, so the title is a weak suggestion",
                        evidence("filename=" + filename));
            }
        }
        if (productType != null) {
            String category = CATEGORY_BY_TYPE.get(DemoSupport.constant(productType));
            out.add("category", category, 0.90, "Merchandising category implied by the product type",
                    evidence("productType=" + productType));
        }
        return DemoReply.answer(support.write(new EnrichmentOutput(out.list(),
                "Offline demo rules proposed %d field(s)%s".formatted(out.size(),
                        guideline == null ? "" : " following '" + guideline + "'"))));
    }

    private Optional<String> guideline(DemoConversation conversation) {
        return conversation.lastToolResult(KNOWLEDGE_TOOL)
                .map(support::parse)
                .filter(JsonNode::isArray)
                .filter(results -> !results.isEmpty())
                .map(results -> results.get(0))
                .map(first -> text(first, "title") + " - " + text(first, "section"));
    }

    private static String possessive(String department) {
        if (department == null) {
            return "";
        }
        return switch (department.toLowerCase(Locale.ROOT)) {
            case "men" -> "men's ";
            case "women" -> "women's ";
            case "kids" -> "kids' ";
            default -> "";
        };
    }

    /** "Regular Fit Oxford Shirt" -> "Oxford shirt". */
    private static String nounPhrase(String productName) {
        return Arrays.stream(productName.split("\\s+"))
                .filter(word -> !FIT_WORDS.contains(word.toLowerCase(Locale.ROOT)))
                .map(word -> PROPER_NOUNS.contains(word.toLowerCase(Locale.ROOT)) ? word : word.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private static List<String> keywords(String productType, String productName, String colorText, String department,
                                         List<String> materials) {
        Set<String> keywords = new LinkedHashSet<>();
        String typeWord = productType == null ? null : humanize(productType).replace(' ', '-');
        if (typeWord != null) {
            keywords.add(typeWord);
        }
        Arrays.stream(productName.toLowerCase(Locale.ROOT).split("[\\s-]+"))
                .filter(word -> !FIT_WORDS.contains(word) && word.length() > 2)
                .filter(word -> typeWord == null || !DemoSupport.singular(word).equals(DemoSupport.singular(typeWord)))
                .forEach(keywords::add);
        if (colorText != null) {
            String[] colorWords = colorText.toLowerCase(Locale.ROOT).split("\\s+");
            keywords.add(colorWords[colorWords.length - 1]);
        }
        if (department != null && Set.of("men", "women", "kids").contains(department.toLowerCase(Locale.ROOT))) {
            keywords.add(department.toLowerCase(Locale.ROOT) + "swear");
        }
        keywords.addAll(materials);
        return new ArrayList<>(keywords);
    }

    private static List<String> tags(String productType, String department, String colorText, String angle) {
        List<String> tags = new ArrayList<>();
        for (String value : new String[] {humanize(productType), department, colorText}) {
            if (value != null) {
                tags.add(value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", ""));
            }
        }
        if (angle != null) {
            tags.add(angle.toLowerCase(Locale.ROOT) + "-view");
        }
        return tags.stream().distinct().toList();
    }
}

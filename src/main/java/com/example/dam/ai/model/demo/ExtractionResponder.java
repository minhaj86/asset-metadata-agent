package com.example.dam.ai.model.demo;

import static com.example.dam.ai.model.demo.DemoSupport.constant;
import static com.example.dam.ai.model.demo.DemoSupport.evidence;
import static com.example.dam.ai.model.demo.DemoSupport.text;
import static com.example.dam.ai.model.demo.DemoSupport.texts;

import com.example.dam.ai.agent.ExtractionOutput;
import com.example.dam.ai.model.demo.DemoSupport.FieldCatalog;
import com.example.dam.ai.model.demo.DemoSupport.Suggestions;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/** Demo rules for the MetadataExtractionAgent: only values directly observable in the supplied evidence. */
final class ExtractionResponder implements DemoResponder {

    private final DemoSupport support;

    ExtractionResponder(DemoSupport support) {
        this.support = support;
    }

    @Override
    public String schemaName() {
        return ExtractionOutput.class.getSimpleName();
    }

    @Override
    public DemoReply reply(DemoConversation conversation) {
        JsonNode context = support.parse(conversation.section("asset_context").orElse(null));
        FieldCatalog catalog = new FieldCatalog(support.parse(conversation.section("field_catalog").orElse("[]")));
        Suggestions out = new Suggestions(catalog, support.parse(conversation.section("current_metadata").orElse(null)));
        JsonNode analysis = context.path("imageAnalysis");
        JsonNode product = context.path("product");
        JsonNode embedded = context.path("embeddedMetadata");
        String filename = String.valueOf(text(context.path("asset"), "filename")).toLowerCase(Locale.ROOT);

        List<String> colors = texts(analysis, "dominantColors");
        if (!colors.isEmpty()) {
            String color = colors.getFirst();
            String productColor = text(product, "color");
            boolean agrees = productColor != null && productColor.toLowerCase(Locale.ROOT).contains(color);
            out.add("color", color, productColor == null ? 0.86 : agrees ? 0.95 : 0.72,
                    agrees ? "Dominant colour from image analysis, consistent with the product colour"
                            : "Dominant colour from image analysis",
                    evidence("imageAnalysis.dominantColors[0]=" + color,
                            productColor == null ? null : "product.color=" + productColor));
            out.add("dominantColor", color, 0.90, "Visually dominant colour reported by image analysis",
                    evidence("imageAnalysis.dominantColors[0]=" + color));
        }
        String orientation = text(analysis, "orientation");
        if (orientation != null) {
            boolean inFilename = filename.contains(orientation);
            out.add("angleFacing", orientation, inFilename ? 0.95 : 0.85,
                    "Orientation detected by image analysis" + (inFilename ? " and stated in the filename" : ""),
                    evidence("imageAnalysis.orientation=" + orientation, inFilename ? "filename=" + filename : null));
        }
        String background = text(analysis, "background");
        out.add("backgroundType", background, 0.96, "Background classified by image analysis",
                evidence("imageAnalysis.background=" + background));
        String shotType = text(analysis, "shotType");
        out.add("shotType", shotType, 0.93, "Shot type classified by image analysis",
                evidence("imageAnalysis.shotType=" + shotType));

        List<String> objects = texts(analysis, "detectedObjects");
        if (!objects.isEmpty()) {
            String object = objects.getFirst();
            String productType = text(product, "productType");
            boolean agrees = productType != null && constant(productType).equals(constant(object));
            out.add("productType", object, agrees ? 0.97 : 0.80,
                    agrees ? "Detected object matches the product type in the product information system"
                            : "Main object detected by image analysis",
                    evidence("imageAnalysis.detectedObjects[0]=" + object,
                            productType == null ? null : "product.productType=" + productType));
        }
        int persons = analysis.path("personCount").asInt(0);
        if (persons > 0) {
            out.add("modelPresent", "true", 0.95, "A person was detected in the image",
                    evidence("imageAnalysis.personCount=" + persons));
            out.add("modelCount", String.valueOf(persons), 0.90, "Number of persons detected",
                    evidence("imageAnalysis.personCount=" + persons));
        }
        String copyright = text(embedded, "exif:Copyright");
        out.add("copyrightOwner", copyright, 0.90, "Copyright notice embedded in the file metadata",
                evidence("embeddedMetadata.exif:Copyright=" + copyright));
        String language = text(embedded, "xmp:dc:language");
        out.add("language", language, 0.90, "Language declared in embedded XMP metadata",
                evidence("embeddedMetadata.xmp:dc:language=" + language));

        List<String> materials = texts(product, "materials");
        if (!materials.isEmpty()) {
            out.add("material", materials, 0.90, "Materials from the product information system",
                    evidence("product.materials=" + materials));
        }
        String brand = text(product, "brand");
        out.add("brand", brand, 0.90, "Brand from the product information system", evidence("product.brand=" + brand));
        String season = text(product, "season");
        out.add("season", season, 0.80, "Season from the product information system", evidence("product.season=" + season));

        return DemoReply.answer(support.write(new ExtractionOutput(out.list(),
                "Offline demo rules derived %d candidate(s) from image analysis, embedded metadata and product data"
                        .formatted(out.size()))));
    }
}

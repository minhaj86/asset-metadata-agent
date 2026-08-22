package com.example.dam.metadata.infrastructure.analysis;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.port.AssetAnalysisService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for a computer-vision service: derives "observations" from filename tokens, the way a
 * vision model would report dominant colours, orientation and detected objects. Replace with a real analyser
 * (vision LLM, cloud vision API) behind the same port.
 */
@Component
class MockAssetAnalysisService implements AssetAnalysisService {

    private static final String ANALYZER = "mock-vision-v1";
    private static final Map<String, String> COLORS = Map.ofEntries(
            Map.entry("blue", "blue"), Map.entry("navy", "navy"), Map.entry("red", "red"),
            Map.entry("white", "white"), Map.entry("black", "black"), Map.entry("grey", "grey"),
            Map.entry("gray", "grey"), Map.entry("beige", "beige"), Map.entry("brown", "brown"),
            Map.entry("green", "green"), Map.entry("pink", "pink"), Map.entry("yellow", "yellow"),
            Map.entry("orange", "orange"), Map.entry("purple", "purple"), Map.entry("denim", "blue"));
    private static final Map<String, String> OBJECTS = new LinkedHashMap<>();
    private static final Set<String> ORIENTATIONS = Set.of("front", "back", "left", "right", "detail");

    static {
        OBJECTS.put("tshirt", "t-shirt");
        OBJECTS.put("tee", "t-shirt");
        OBJECTS.put("shirt", "shirt");
        OBJECTS.put("dress", "dress");
        OBJECTS.put("chinos", "trousers");
        OBJECTS.put("trousers", "trousers");
        OBJECTS.put("pants", "trousers");
        OBJECTS.put("jeans", "jeans");
        OBJECTS.put("jacket", "jacket");
        OBJECTS.put("coat", "jacket");
        OBJECTS.put("sweater", "knitwear");
        OBJECTS.put("knit", "knitwear");
        OBJECTS.put("sneakers", "sneakers");
        OBJECTS.put("boots", "boots");
        OBJECTS.put("bag", "bag");
        OBJECTS.put("cushion", "cushion");
    }

    @Override
    public AssetAnalysis analyze(Asset asset) {
        if (asset.type() == AssetType.DOCUMENT || !asset.mimeType().startsWith("image/")) {
            return new AssetAnalysis(List.of(), null, null, List.of(), 0, null, List.of(), ANALYZER);
        }
        List<String> tokens = tokens(asset.filename());
        List<String> colors = new ArrayList<>();
        if (tokens.contains("light") && tokens.contains("blue")) {
            colors.add("light blue");
        }
        tokens.stream().map(COLORS::get).filter(color -> color != null && !colors.contains(color)).forEach(colors::add);
        List<String> objects = tokens.stream().map(OBJECTS::get).filter(object -> object != null).distinct().toList();
        String orientation = tokens.stream().filter(ORIENTATIONS::contains).findFirst()
                .orElse(tokens.contains("side") ? "left" : null);
        int persons = tokens.contains("models") ? 2 : tokens.contains("model") ? 1 : 0;
        boolean campaign = tokens.contains("campaign") || tokens.contains("hero") || tokens.contains("lifestyle");
        String background = campaign ? "outdoor" : "white";
        String shotType = persons > 0 ? "on_model"
                : "detail".equals(orientation) ? "detail"
                : campaign ? "lifestyle"
                : tokens.contains("flatlay") ? "flat_lay" : "studio";
        return new AssetAnalysis(colors, orientation, background, objects, persons, shotType, List.of(), ANALYZER);
    }

    private static List<String> tokens(String filename) {
        String base = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        String normalized = base.toLowerCase(Locale.ROOT).replace("t-shirt", "tshirt");
        return Arrays.stream(normalized.split("[^a-z0-9]+")).filter(token -> !token.isBlank()).toList();
    }
}

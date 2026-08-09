package com.example.dam.asset.infrastructure.dam;

import com.example.dam.asset.application.port.DamClient.DamAssetRecord;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders deterministic SVG placeholder previews for the mock DAM, derived from filename tokens (colour, garment,
 * orientation), the way a real DAM serves renditions of binaries it owns. Clearly labelled as mock output.
 */
final class MockRenditionRenderer {

    private static final Map<String, String> COLORS = new LinkedHashMap<>();
    private static final String DEFAULT_COLOR = "#a1a1aa";

    static {
        COLORS.put("navy", "#1e2b4a");
        COLORS.put("denim", "#4a6c9b");
        COLORS.put("blue", "#3b73b9");
        COLORS.put("red", "#c0392b");
        COLORS.put("white", "#fafafa");
        COLORS.put("black", "#262626");
        COLORS.put("grey", "#8c8c8c");
        COLORS.put("gray", "#8c8c8c");
        COLORS.put("beige", "#d9c4a3");
        COLORS.put("brown", "#7a5235");
        COLORS.put("green", "#3f7d4e");
        COLORS.put("pink", "#e9a3b8");
        COLORS.put("yellow", "#e8c547");
        COLORS.put("orange", "#e07b39");
        COLORS.put("purple", "#7b4f9e");
    }

    String render(DamAssetRecord asset) {
        List<String> tokens = Arrays.stream(asset.filename().toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> !token.isBlank())
                .toList();
        String color = color(tokens);
        boolean campaign = tokens.contains("campaign") || tokens.contains("hero") || tokens.contains("lifestyle");
        boolean model = tokens.contains("model") || tokens.contains("models");
        boolean detail = tokens.contains("detail");
        boolean back = tokens.contains("back");
        StringBuilder svg = new StringBuilder("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 600" width="480" height="600" role="img">
                <defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">%s</linearGradient></defs>
                <rect width="480" height="600" fill="url(#bg)"/>
                """.formatted(campaign
                ? "<stop offset=\"0\" stop-color=\"#cfe3f7\"/><stop offset=\"1\" stop-color=\"#f6e7c8\"/>"
                : "<stop offset=\"0\" stop-color=\"#f5f5f4\"/><stop offset=\"1\" stop-color=\"#e7e5e4\"/>"));
        if (campaign) {
            svg.append(campaignScene(color));
        } else {
            svg.append(detail ? "<g transform=\"translate(-260,-250) scale(2.1)\">" : "<g>");
            if (model) {
                svg.append("<circle cx=\"240\" cy=\"95\" r=\"38\" fill=\"#d8b598\"/>"
                        + "<rect x=\"226\" y=\"126\" width=\"28\" height=\"22\" fill=\"#d8b598\"/>");
            }
            svg.append(garment(tokens, color, back));
            svg.append("</g>");
        }
        svg.append("""
                <rect x="0" y="548" width="480" height="52" fill="#18181b" fill-opacity="0.82"/>
                <text x="16" y="569" font-family="ui-sans-serif,system-ui,sans-serif" font-size="12" fill="#a1a1aa" \
                letter-spacing="1.5">MOCK DAM PREVIEW</text>
                <text x="16" y="588" font-family="ui-monospace,monospace" font-size="13" fill="#fafafa">%s · %s</text>
                </svg>
                """.formatted(escape(asset.externalAssetId()), escape(asset.filename())));
        return svg.toString();
    }

    private static String color(List<String> tokens) {
        if (tokens.contains("light") && tokens.contains("blue")) {
            return "#9cc4e8";
        }
        return tokens.stream().map(COLORS::get).filter(value -> value != null).findFirst().orElse(DEFAULT_COLOR);
    }

    private static String garment(List<String> tokens, String color, boolean back) {
        String stroke = " stroke=\"#00000030\" stroke-width=\"3\" stroke-linejoin=\"round\"";
        String fill = " fill=\"" + color + "\"" + stroke;
        if (tokens.contains("dress")) {
            return "<path d=\"M200,150 Q240,172 280,150 L302,162 L286,262 L352,500 L128,500 L194,262 L178,162 Z\"" + fill + "/>"
                    + "<path d=\"M194,262 Q240,276 286,262\" fill=\"none\"" + stroke + "/>";
        }
        if (tokens.contains("chinos") || tokens.contains("trousers") || tokens.contains("jeans") || tokens.contains("pants")) {
            return "<path d=\"M168,140 L312,140 L334,500 L262,500 L240,262 L218,500 L146,500 Z\"" + fill + "/>"
                    + "<path d=\"M168,170 L312,170\" fill=\"none\"" + stroke + "/>";
        }
        if (tokens.contains("sneakers") || tokens.contains("boots")) {
            return "<path d=\"M96,400 Q140,300 236,322 L300,334 Q356,346 388,404 L388,430 L96,430 Z\"" + fill + "/>"
                    + "<rect x=\"92\" y=\"430\" width=\"300\" height=\"22\" rx=\"8\" fill=\"#f4f4f5\"" + stroke + "/>"
                    + "<path d=\"M210,330 L232,372 M236,326 L256,368 M262,330 L280,370\" fill=\"none\"" + stroke + "/>";
        }
        boolean jacket = tokens.contains("jacket") || tokens.contains("coat");
        boolean knit = tokens.contains("sweater") || tokens.contains("knit");
        boolean tshirt = tokens.contains("tshirt") || tokens.contains("tee") || tokens.contains("t") && tokens.contains("shirt");
        if (tshirt) {
            return "<path d=\"M176,150 Q240,190 304,150 L372,192 L346,246 L316,226 L316,480 L164,480 L164,226 L134,246 L108,192 Z\""
                    + fill + "/>";
        }
        int bottom = jacket ? 510 : 480;
        StringBuilder shape = new StringBuilder("<path d=\"M168,160 L210,140 Q240,160 270,140 L312,160 L386,222 L352,268 "
                + "L322,240 L322," + bottom + " L158," + bottom + " L158,240 L128,268 L94,222 Z\"" + fill + "/>");
        if (knit) {
            shape.append("<path d=\"M158,").append(bottom - 26).append(" L322,").append(bottom - 26)
                    .append("\" fill=\"none\"").append(stroke).append("/>");
        } else if (!back) {
            shape.append("<path d=\"M210,140 L240,").append(jacket ? 230 : 186).append(" L270,140\" fill=\"none\"")
                    .append(stroke).append("/>")
                    .append("<path d=\"M240,").append(jacket ? 230 : 186).append(" L240,").append(bottom)
                    .append("\" fill=\"none\"").append(stroke).append("/>");
            for (int y = jacket ? 270 : 220; y < bottom - 20; y += 52) {
                shape.append("<circle cx=\"250\" cy=\"").append(y).append("\" r=\"5\" fill=\"#00000040\"/>");
            }
        }
        return shape.toString();
    }

    private static String campaignScene(String accent) {
        return "<circle cx=\"370\" cy=\"120\" r=\"52\" fill=\"#fbbf24\" fill-opacity=\"0.85\"/>"
                + "<path d=\"M0,430 Q120,390 240,420 T480,410 L480,548 L0,548 Z\" fill=\"#e9d5a1\"/>"
                + figure(150, accent) + figure(240, "#c0392b") + figure(330, "#3f7d4e");
    }

    private static String figure(int x, String color) {
        return "<circle cx=\"" + x + "\" cy=\"262\" r=\"24\" fill=\"#d8b598\"/>"
                + "<path d=\"M" + (x - 34) + ",300 Q" + x + ",284 " + (x + 34) + ",300 L" + (x + 28) + ",430 L"
                + (x - 28) + ",430 Z\" fill=\"" + color + "\"/>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

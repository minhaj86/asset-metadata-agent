package com.example.dam.metadata.application.port;

import com.example.dam.asset.domain.Asset;
import java.util.List;

/**
 * Port for visual analysis of the binary (computer vision). Mocked in this POC; a vision model or an external
 * analysis service can be plugged in without touching the enrichment workflow.
 */
public interface AssetAnalysisService {

    AssetAnalysis analyze(Asset asset);

    record AssetAnalysis(
            List<String> dominantColors,
            String orientation,
            String background,
            List<String> detectedObjects,
            int personCount,
            String shotType,
            List<String> ocrText,
            String analyzer) {

        public AssetAnalysis {
            dominantColors = dominantColors == null ? List.of() : List.copyOf(dominantColors);
            detectedObjects = detectedObjects == null ? List.of() : List.copyOf(detectedObjects);
            ocrText = ocrText == null ? List.of() : List.copyOf(ocrText);
        }
    }
}

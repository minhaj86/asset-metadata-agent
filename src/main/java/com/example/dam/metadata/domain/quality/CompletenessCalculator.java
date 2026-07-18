package com.example.dam.metadata.domain.quality;

import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import java.util.List;
import java.util.Set;

public final class CompletenessCalculator {

    public CompletenessScore calculate(MetadataSchema schema, AssetMetadataSnapshot metadata) {
        List<String> missingRequired = missing(schema.requiredFields(), metadata);
        List<String> missingRecommended = missing(schema.recommendedFields(), metadata);
        int requiredTotal = schema.requiredFields().size();
        int recommendedTotal = schema.recommendedFields().size();
        return new CompletenessScore(
                requiredTotal, requiredTotal - missingRequired.size(),
                recommendedTotal, recommendedTotal - missingRecommended.size(),
                missingRequired, missingRecommended);
    }

    private static List<String> missing(Set<String> fields, AssetMetadataSnapshot metadata) {
        return fields.stream().filter(field -> !metadata.has(field)).toList();
    }
}

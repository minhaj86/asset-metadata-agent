package com.example.dam.metadata.application.enrichment;

import com.example.dam.metadata.domain.value.MetadataValue;
import java.util.List;
import java.util.Objects;

/**
 * A typed, schema-conformant metadata suggestion produced by the AI layer. Every candidate carries confidence,
 * explanation and evidence; the AI layer never decides whether it is applied.
 *
 * @param proposedBy name of the agent that produced it
 */
public record MetadataCandidate(
        String fieldKey,
        MetadataValue value,
        double confidence,
        String explanation,
        List<String> evidence,
        String proposedBy,
        String aiModel) {

    public MetadataCandidate {
        Objects.requireNonNull(fieldKey, "fieldKey");
        Objects.requireNonNull(value, "value");
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be within [0,1]");
        }
    }
}

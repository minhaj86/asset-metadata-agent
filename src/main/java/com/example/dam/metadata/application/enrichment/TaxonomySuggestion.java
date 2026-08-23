package com.example.dam.metadata.application.enrichment;

/**
 * Outcome of taxonomy classification.
 *
 * @param accepted        whether the suggested node exists and was turned into a candidate
 * @param rejectionReason why a suggestion was discarded (e.g. invented node)
 */
public record TaxonomySuggestion(String nodeId, String path, double confidence, String reason, boolean accepted,
                                 String rejectionReason) {
}

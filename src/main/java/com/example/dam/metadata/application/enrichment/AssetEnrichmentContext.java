package com.example.dam.metadata.application.enrichment;

import com.example.dam.asset.domain.Asset;
import com.example.dam.metadata.application.port.AssetAnalysisService.AssetAnalysis;
import com.example.dam.metadata.application.port.ProductInformationClient.ProductInformation;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.taxonomy.domain.TaxonomyNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything known about an asset before AI reasoning starts, assembled deterministically.
 *
 * @param embeddedMetadata   EXIF/XMP/IPTC from the DAM
 * @param imageAnalysis      computer-vision observations, {@code null} if unavailable
 * @param product            product information, {@code null} if the asset has no (known) product
 * @param taxonomyCandidates valid taxonomy nodes pre-selected by keyword search
 * @param warnings           degraded inputs (e.g. product system unavailable)
 */
public record AssetEnrichmentContext(
        Asset asset,
        MetadataSchema schema,
        AssetMetadataSnapshot currentMetadata,
        Map<String, String> embeddedMetadata,
        AssetAnalysis imageAnalysis,
        ProductInformation product,
        List<TaxonomyNode> taxonomyCandidates,
        List<String> warnings) {

    public AssetEnrichmentContext {
        embeddedMetadata = Map.copyOf(embeddedMetadata);
        taxonomyCandidates = List.copyOf(taxonomyCandidates);
        warnings = List.copyOf(warnings);
    }

    public Optional<AssetAnalysis> analysis() {
        return Optional.ofNullable(imageAnalysis);
    }

    public Optional<ProductInformation> productInformation() {
        return Optional.ofNullable(product);
    }
}

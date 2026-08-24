package com.example.dam.metadata.application.enrichment;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetNotFoundException;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.asset.application.port.DamClient;
import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.application.port.AssetAnalysisService;
import com.example.dam.metadata.application.port.AssetAnalysisService.AssetAnalysis;
import com.example.dam.metadata.application.port.ProductInformationClient;
import com.example.dam.metadata.application.port.ProductInformationClient.ProductInformation;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.schema.WellKnownFields;
import com.example.dam.taxonomy.application.TaxonomyService;
import com.example.dam.taxonomy.domain.TaxonomyNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministically gathers the context for enrichment from internal data and external systems. Integration
 * failures degrade the context (with warnings) instead of failing the enrichment.
 */
@Service
public class EnrichmentContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentContextAssembler.class);
    private static final int TAXONOMY_CANDIDATES = 8;

    private final AssetRepository assetRepository;
    private final AssetMetadataRepository metadataRepository;
    private final MetadataSchemaService schemaService;
    private final DamClient damClient;
    private final ProductInformationClient productClient;
    private final AssetAnalysisService analysisService;
    private final TaxonomyService taxonomyService;

    public EnrichmentContextAssembler(AssetRepository assetRepository, AssetMetadataRepository metadataRepository,
                                      MetadataSchemaService schemaService, DamClient damClient,
                                      ProductInformationClient productClient, AssetAnalysisService analysisService,
                                      TaxonomyService taxonomyService) {
        this.assetRepository = assetRepository;
        this.metadataRepository = metadataRepository;
        this.schemaService = schemaService;
        this.damClient = damClient;
        this.productClient = productClient;
        this.analysisService = analysisService;
        this.taxonomyService = taxonomyService;
    }

    @Transactional(readOnly = true)
    public AssetEnrichmentContext assemble(UUID assetId) {
        Asset asset = assetRepository.findById(assetId).orElseThrow(() -> new AssetNotFoundException(assetId));
        AssetMetadataSnapshot metadata = AssetMetadataSnapshot.of(asset.id(), metadataRepository.findByAssetId(asset.id()));
        List<String> warnings = new ArrayList<>();

        Map<String, String> embedded = degradeGracefully(() -> damClient.getMetadata(asset.externalAssetId()),
                Map.of(), "Embedded DAM metadata unavailable", warnings);
        AssetAnalysis analysis = degradeGracefully(() -> analysisService.analyze(asset), null,
                "Image analysis unavailable", warnings);
        ProductInformation product = metadata.value(WellKnownFields.PRODUCT_ID)
                .map(value -> value.tokens().getFirst())
                .map(productId -> degradeGracefully(() -> productClient.findByProductId(productId)
                                .orElseGet(() -> {
                                    warnings.add("Product %s is unknown to the product information system".formatted(productId));
                                    return null;
                                }),
                        null, "Product information system unavailable", warnings))
                .orElse(null);
        MetadataSchema schema = schemaService.schemaFor(asset.type());
        List<TaxonomyNode> candidates = asset.type().isTaxonomyClassifiable()
                ? taxonomyService.search(taxonomyQuery(schema, metadata, analysis, product), TAXONOMY_CANDIDATES)
                : List.of();

        return new AssetEnrichmentContext(asset, schema, metadata, embedded, analysis, product, candidates, warnings);
    }

    /** Keywords from product data, vision output and classifying (controlled-vocabulary) metadata. */
    private static String taxonomyQuery(MetadataSchema schema, AssetMetadataSnapshot metadata, AssetAnalysis analysis,
                                        ProductInformation product) {
        List<String> terms = new ArrayList<>();
        if (product != null) {
            terms.add(product.productType());
            terms.add(product.department());
            terms.add(product.name());
        }
        metadata.entries().forEach((key, entry) -> schema.field(key)
                .filter(field -> field.semanticSearch() && field.hasControlledVocabulary())
                .ifPresent(field -> terms.add(entry.value().display())));
        if (analysis != null) {
            terms.addAll(analysis.detectedObjects());
        }
        return String.join(" ", terms.stream().filter(term -> term != null && !term.isBlank()).toList())
                .replace('_', ' ');
    }

    private static <T> T degradeGracefully(Supplier<T> call, T fallback, String warning, List<String> warnings) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            log.warn("{}: {}", warning, e.getMessage());
            warnings.add(warning);
            return fallback;
        }
    }
}

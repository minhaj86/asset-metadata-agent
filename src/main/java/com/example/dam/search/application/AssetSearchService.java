package com.example.dam.search.application;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.common.domain.InvalidRequestException;
import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.schema.CrossFieldRule;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchemaDefinition;
import com.example.dam.search.application.AssetSearchCriteria.MetadataFilter;
import com.example.dam.search.application.port.AssetEmbeddingRepository;
import com.example.dam.search.application.port.AssetSearchRepository;
import com.example.dam.search.application.port.AssetSearchRepository.SearchQuery;
import com.example.dam.search.application.port.AssetSearchRepository.SearchRow;
import com.example.dam.search.application.port.TextEmbedder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Deterministic hybrid search: structured metadata filters narrow the candidate set, vector similarity ranks it.
 * If the embedding model or vector store is unavailable the search degrades to structured results.
 */
@Service
public class AssetSearchService {

    private static final Logger log = LoggerFactory.getLogger(AssetSearchService.class);

    private final AssetSearchRepository searchRepository;
    private final AssetEmbeddingRepository embeddingRepository;
    private final AssetRepository assetRepository;
    private final AssetMetadataRepository metadataRepository;
    private final MetadataSchemaService schemaService;
    private final ObjectProvider<TextEmbedder> embedder;
    private final SearchProperties properties;

    public AssetSearchService(AssetSearchRepository searchRepository, AssetEmbeddingRepository embeddingRepository,
                              AssetRepository assetRepository, AssetMetadataRepository metadataRepository,
                              MetadataSchemaService schemaService, ObjectProvider<TextEmbedder> embedder,
                              SearchProperties properties) {
        this.searchRepository = searchRepository;
        this.embeddingRepository = embeddingRepository;
        this.assetRepository = assetRepository;
        this.metadataRepository = metadataRepository;
        this.schemaService = schemaService;
        this.embedder = embedder;
        this.properties = properties;
    }

    public record AssetSearchHit(Asset asset, Double similarity, AssetMetadataSnapshot metadata) {
    }

    /**
     * @param criteria        normalised criteria that were actually executed
     * @param semanticApplied whether vector ranking was used
     * @param degraded        semantic part was requested but unavailable
     */
    public record AssetSearchResult(AssetSearchCriteria criteria, List<AssetSearchHit> hits, boolean semanticApplied,
                                    boolean degraded, List<String> warnings) {
    }

    public AssetSearchResult search(AssetSearchCriteria requested) {
        MetadataSchemaDefinition schema = schemaService.definition();
        AssetSearchCriteria criteria = normalize(requested, schema);
        List<String> warnings = new ArrayList<>();
        String rightsField = criteria.hasRightsWindow() ? rightsExpiryField(schema) : null;
        int limit = criteria.limit();

        float[] vector = criteria.isSemantic() ? queryVector(criteria, warnings) : null;
        boolean degraded = criteria.isSemantic() && vector == null;
        Double minSimilarity = vector != null && criteria.filters().isEmpty() && criteria.similarToAssetId() == null
                ? properties.semanticMinSimilarity() : null;
        SearchQuery query = new SearchQuery(criteria.filters(), criteria.missingFields(), criteria.assetType(),
                rightsField, criteria.rightsExpiryAfter(), criteria.rightsExpiryBefore(), vector,
                criteria.similarToAssetId(), minSimilarity, limit);

        List<SearchRow> rows;
        try {
            rows = searchRepository.search(query);
        } catch (DataAccessException e) {
            if (vector == null) {
                throw e;
            }
            log.warn("Vector search failed, falling back to structured search: {}", e.getMostSpecificCause().getMessage());
            warnings.add("Vector store unavailable; results are not semantically ranked");
            degraded = true;
            vector = null;
            rows = searchRepository.search(new SearchQuery(criteria.filters(), criteria.missingFields(),
                    criteria.assetType(), rightsField, criteria.rightsExpiryAfter(), criteria.rightsExpiryBefore(),
                    null, criteria.similarToAssetId(), null, limit));
        }
        return new AssetSearchResult(criteria, hydrate(rows), vector != null, degraded, warnings);
    }

    private AssetSearchCriteria normalize(AssetSearchCriteria criteria, MetadataSchemaDefinition schema) {
        List<MetadataFilter> filters = criteria.filters().stream().map(filter -> {
            MetadataFieldDefinition field = schema.field(filter.field())
                    .filter(MetadataFieldDefinition::searchable)
                    .orElseThrow(() -> new InvalidRequestException("UNSEARCHABLE_FIELD",
                            "Field '%s' does not exist or is not searchable".formatted(filter.field())));
            String value = filter.value().strip();
            if (field.hasControlledVocabulary()) {
                value = field.canonicalAllowedValue(value).orElseThrow(() -> new InvalidRequestException(
                        "VALUE_NOT_ALLOWED", "Value '%s' is not allowed for '%s': %s"
                        .formatted(filter.value(), field.key(), field.allowedValues())));
            }
            return new MetadataFilter(field.key(), value);
        }).toList();
        criteria.missingFields().forEach(field -> {
            if (schema.field(field).isEmpty()) {
                throw new InvalidRequestException("UNKNOWN_FIELD", "Unknown metadata field: " + field);
            }
        });
        if (criteria.rightsExpiryAfter() != null && criteria.rightsExpiryBefore() != null
                && criteria.rightsExpiryAfter().isAfter(criteria.rightsExpiryBefore())) {
            throw new InvalidRequestException("INVALID_DATE_RANGE", "rightsExpiryAfter must not be after rightsExpiryBefore");
        }
        int limit = criteria.limit() == null ? properties.defaultLimit()
                : Math.clamp(criteria.limit(), 1, properties.maxLimit());
        return new AssetSearchCriteria(filters, criteria.semanticQuery(), criteria.rightsExpiryAfter(),
                criteria.rightsExpiryBefore(), criteria.missingFields(), criteria.assetType(),
                criteria.similarToAssetId(), limit);
    }

    private float[] queryVector(AssetSearchCriteria criteria, List<String> warnings) {
        if (criteria.similarToAssetId() != null) {
            Optional<float[]> reference = embeddingRepository.findVector(criteria.similarToAssetId());
            if (reference.isPresent()) {
                return reference.get();
            }
            warnings.add("Reference asset %s has no embedding yet".formatted(criteria.similarToAssetId()));
        }
        if (criteria.semanticQuery() == null) {
            return null;
        }
        TextEmbedder textEmbedder = embedder.getIfAvailable();
        if (textEmbedder == null) {
            warnings.add("Semantic search is disabled; structured results only");
            return null;
        }
        try {
            return textEmbedder.embed(criteria.semanticQuery());
        } catch (RuntimeException e) {
            log.warn("Embedding the search query failed: {}", e.getMessage());
            warnings.add("Embedding model unavailable; results are not semantically ranked");
            return null;
        }
    }

    private static String rightsExpiryField(MetadataSchemaDefinition schema) {
        return schema.rules().stream()
                .filter(CrossFieldRule.RightsExpiry.class::isInstance)
                .map(rule -> ((CrossFieldRule.RightsExpiry) rule).field())
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("NO_RIGHTS_FIELD",
                        "The metadata schema defines no usage-rights expiry field"));
    }

    private List<AssetSearchHit> hydrate(List<SearchRow> rows) {
        List<UUID> ids = rows.stream().map(SearchRow::assetId).toList();
        Map<UUID, Asset> assets = assetRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Asset::id, Function.identity()));
        Map<UUID, List<AssetMetadata>> metadata = metadataRepository.findByAssetIds(ids);
        return rows.stream()
                .filter(row -> assets.containsKey(row.assetId()))
                .map(row -> new AssetSearchHit(assets.get(row.assetId()), row.similarity(),
                        AssetMetadataSnapshot.of(row.assetId(), metadata.getOrDefault(row.assetId(), List.of()))))
                .toList();
    }
}

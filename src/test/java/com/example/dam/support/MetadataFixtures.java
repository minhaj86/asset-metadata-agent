package com.example.dam.support;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.common.domain.Actor;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.MetadataStatus;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.schema.MetadataSchemaDefinition;
import com.example.dam.metadata.domain.validation.MetadataValueParser;
import com.example.dam.metadata.domain.validation.ReferenceDataLookup;
import com.example.dam.metadata.domain.value.MetadataValue;
import com.example.dam.metadata.infrastructure.schema.YamlMetadataSchemaParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Test fixtures based on the shipped YAML schema, so tests also guard the real configuration. */
public final class MetadataFixtures {

    public static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    public static final Set<String> TAXONOMY_PATHS = Set.of(
            "Fashion > Men > Clothing > Shirts", "Fashion > Women > Clothing > Dresses");
    public static final ReferenceDataLookup REFERENCE_DATA = TAXONOMY_PATHS::contains;

    private static final MetadataSchemaDefinition DEFINITION = load();
    private static final MetadataValueParser PARSER = new MetadataValueParser();

    private MetadataFixtures() {
    }

    public static MetadataSchemaDefinition definition() {
        return DEFINITION;
    }

    public static MetadataSchema schema(AssetType type) {
        return DEFINITION.forAssetType(type);
    }

    public static MetadataFieldDefinition field(String key) {
        return DEFINITION.field(key).orElseThrow(() -> new IllegalArgumentException("No field " + key));
    }

    /** Typed value parsed with the schema, like the application does for API input. */
    public static MetadataValue value(String key, Object raw) {
        return PARSER.parse(field(key), raw).valueOrThrow();
    }

    public static AssetMetadata entry(UUID assetId, String key, MetadataValue value, MetadataSource source,
                                      MetadataStatus status) {
        return AssetMetadata.create(assetId, key, value, source, source == MetadataSource.AI_GENERATED ? 0.9 : null,
                status, null, Actor.human("tester"), NOW);
    }

    public static AssetMetadata approved(String key, Object raw) {
        return entry(UUID.randomUUID(), key, value(key, raw), MetadataSource.MANUAL, MetadataStatus.APPROVED);
    }

    public static AssetMetadata aiValidated(String key, Object raw) {
        return entry(UUID.randomUUID(), key, value(key, raw), MetadataSource.AI_GENERATED, MetadataStatus.VALIDATED);
    }

    /** Snapshot with manual, approved values; {@link MetadataValue}s are used as-is, anything else is parsed. */
    public static AssetMetadataSnapshot snapshot(UUID assetId, Map<String, Object> values) {
        List<AssetMetadata> entries = new ArrayList<>();
        values.forEach((key, raw) -> {
            MetadataValue value = raw instanceof MetadataValue typed ? typed
                    : DEFINITION.field(key).isPresent() ? value(key, raw) : PARSER.parseUntyped(raw);
            entries.add(entry(assetId, key, value, MetadataSource.MANUAL, MetadataStatus.APPROVED));
        });
        return AssetMetadataSnapshot.of(assetId, entries);
    }

    public static Asset asset(AssetType type, String filename) {
        return new Asset(UUID.randomUUID(), "TEST-" + UUID.randomUUID().toString().substring(0, 8), filename,
                "image/jpeg", URI.create("https://dam.example.com/assets/" + filename), type, AssetStatus.DRAFT, 0,
                NOW, NOW);
    }

    /** A complete, valid PRODUCT_IMAGE (relative to {@link #NOW}). */
    public static Map<String, Object> completeProductImage() {
        return Map.ofEntries(
                Map.entry("productId", "12345"),
                Map.entry("productType", "SHIRT"),
                Map.entry("color", "BLUE"),
                Map.entry("angleFacing", "FRONT"),
                Map.entry("shotType", "STUDIO"),
                Map.entry("copyrightOwner", "Sample Apparel"),
                Map.entry("usageRightsStart", "2026-01-01"),
                Map.entry("usageRightsEnd", "2027-12-31"),
                Map.entry("altText", "Front view of a light blue men's Oxford shirt on a white background"),
                Map.entry("title", "Regular Fit Oxford Shirt - Front View"),
                Map.entry("keywords", List.of("shirt", "oxford", "blue")),
                Map.entry("taxonomyPath", "Fashion > Men > Clothing > Shirts"),
                Map.entry("backgroundType", "WHITE"),
                Map.entry("material", List.of("COTTON")),
                Map.entry("description", "Studio image of the Regular Fit Oxford Shirt in light blue cotton."));
    }

    private static MetadataSchemaDefinition load() {
        try (InputStream in = MetadataFixtures.class.getResourceAsStream("/metadata-schema/dam-metadata-schema.yaml")) {
            return new YamlMetadataSchemaParser().parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

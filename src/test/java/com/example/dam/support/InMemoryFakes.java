package com.example.dam.support;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.taxonomy.domain.TaxonomyNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Small in-memory fakes for unit tests. */
public final class InMemoryFakes {

    private InMemoryFakes() {
    }

    public static List<TaxonomyNode> taxonomy() {
        return List.of(
                node("fashion", null, "Fashion", "Fashion", 0, List.of("apparel"), false),
                node("fashion.men", "fashion", "Men", "Fashion > Men", 1, List.of("men", "menswear"), false),
                node("fashion.men.clothing", "fashion.men", "Clothing", "Fashion > Men > Clothing", 2, List.of(), false),
                node("fashion.men.clothing.shirts", "fashion.men.clothing", "Shirts", "Fashion > Men > Clothing > Shirts", 3,
                        List.of("shirt", "oxford"), true),
                node("fashion.men.clothing.t-shirts", "fashion.men.clothing", "T-shirts",
                        "Fashion > Men > Clothing > T-shirts", 3, List.of("t-shirt", "tee"), true),
                node("fashion.women", "fashion", "Women", "Fashion > Women", 1, List.of("women"), false),
                node("fashion.women.clothing", "fashion.women", "Clothing", "Fashion > Women > Clothing", 2, List.of(), false),
                node("fashion.women.clothing.dresses", "fashion.women.clothing", "Dresses",
                        "Fashion > Women > Clothing > Dresses", 3, List.of("dress"), true),
                node("fashion.women.clothing.tops", "fashion.women.clothing", "Tops & Blouses",
                        "Fashion > Women > Clothing > Tops & Blouses", 3, List.of("top", "blouse", "shirt"), true));
    }

    private static TaxonomyNode node(String id, String parent, String name, String path, int depth,
                                     List<String> synonyms, boolean leaf) {
        return new TaxonomyNode(id, parent, name, path, depth, synonyms, "", leaf);
    }

    /** Minimal asset repository supporting the lookups used by application services under test. */
    public static final class AssetStore implements AssetRepository {

        private final Map<UUID, Asset> assets = new ConcurrentHashMap<>();

        public Asset add(Asset asset) {
            assets.put(asset.id(), asset);
            return asset;
        }

        @Override
        public void insert(Asset asset) {
            add(asset);
        }

        @Override
        public Optional<Asset> findById(UUID id) {
            return Optional.ofNullable(assets.get(id));
        }

        @Override
        public Optional<Asset> findByExternalAssetId(String externalAssetId) {
            return assets.values().stream().filter(asset -> asset.externalAssetId().equals(externalAssetId)).findFirst();
        }

        @Override
        public List<Asset> findAllById(List<UUID> ids) {
            return ids.stream().map(assets::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public List<Asset> findPage(AssetType type, int offset, int limit) {
            return assets.values().stream().skip(offset).limit(limit).toList();
        }

        @Override
        public Optional<Asset> lockById(UUID id) {
            return findById(id);
        }

        @Override
        public void updateStatus(UUID id, AssetStatus status, Instant now) {
            assets.computeIfPresent(id, (key, asset) -> asset.withStatus(status, now));
        }

        @Override
        public long incrementMetadataRevision(UUID id, Instant now) {
            return 1;
        }
    }
}

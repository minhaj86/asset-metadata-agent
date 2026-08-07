package com.example.dam.asset.infrastructure.dam;

import com.example.dam.asset.application.port.DamClient;
import com.example.dam.common.config.IntegrationProperties;
import com.example.dam.common.config.YamlResources;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * In-memory stand-in for an enterprise DAM. A real adapter would translate these calls to the DAM API.
 */
@Component
class MockDamClient implements DamClient {

    private static final Logger log = LoggerFactory.getLogger(MockDamClient.class);

    private final Map<String, DamAssetRecord> catalog = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> writtenBack = new ConcurrentHashMap<>();

    MockDamClient(ResourceLoader resourceLoader, IntegrationProperties properties) {
        YamlResources.loadList(resourceLoader.getResource(properties.damCatalog()), "assets").forEach(entry -> {
            Map<String, String> embedded = new LinkedHashMap<>();
            Object rawEmbedded = entry.get("embedded");
            if (rawEmbedded instanceof Map<?, ?> map) {
                map.forEach((key, value) -> embedded.put(String.valueOf(key), String.valueOf(value)));
            }
            String id = String.valueOf(entry.get("externalAssetId"));
            String filename = String.valueOf(entry.get("filename"));
            Object url = entry.get("url");
            catalog.put(id, new DamAssetRecord(id, filename, String.valueOf(entry.get("mimeType")),
                    url != null ? URI.create(String.valueOf(url)) : renditionUrl(properties.mockDamBaseUrl(), id, filename),
                    Map.copyOf(embedded)));
        });
        log.info("Mock DAM initialised with {} assets", catalog.size());
    }

    @Override
    public Optional<DamAssetRecord> getAsset(String externalAssetId) {
        return Optional.ofNullable(catalog.get(externalAssetId));
    }

    @Override
    public Map<String, String> getMetadata(String externalAssetId) {
        return getAsset(externalAssetId).map(DamAssetRecord::embeddedMetadata).orElse(Map.of());
    }

    @Override
    public void updateMetadata(String externalAssetId, Map<String, String> metadata) {
        writtenBack.computeIfAbsent(externalAssetId, id -> new ConcurrentHashMap<>()).putAll(metadata);
        log.info("Mock DAM write-back: {} field(s) for {}", metadata.size(), externalAssetId);
    }

    @Override
    public List<DamAssetRecord> searchAssets(String query, int limit) {
        List<String> terms = query == null ? List.of()
                : Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+")).filter(term -> !term.isBlank()).toList();
        return catalog.values().stream()
                .filter(record -> terms.stream().allMatch(term ->
                        record.filename().toLowerCase(Locale.ROOT).contains(term)
                                || record.externalAssetId().toLowerCase(Locale.ROOT).contains(term)))
                .sorted((a, b) -> a.externalAssetId().compareTo(b.externalAssetId()))
                .limit(limit)
                .toList();
    }

    @Override
    public DamHealth health() {
        return new DamHealth(true, "Mock DAM", "%d catalog assets (in-memory POC adapter)".formatted(catalog.size()));
    }

        /** Asset URL served by {@link MockDamRenditionController}: {@code <base>/assets/<id>/<filename>}. */
    static URI renditionUrl(String baseUrl, String externalAssetId, String filename) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(base + "/assets/" + UriUtils.encodePathSegment(externalAssetId, StandardCharsets.UTF_8)
                + "/" + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8));
    }

        Map<String, String> writtenBack(String externalAssetId) {
        return writtenBack.getOrDefault(externalAssetId, Map.of());
    }
}

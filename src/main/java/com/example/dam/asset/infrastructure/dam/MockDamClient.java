package com.example.dam.asset.infrastructure.dam;

import com.example.dam.asset.application.port.DamClient;
import com.example.dam.common.config.IntegrationProperties;
import com.example.dam.common.config.YamlResources;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
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
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "gif", "image/gif", "webp", "image/webp");
    private static final int IMAGE_ID_BASE = 300_000;

    private final Map<String, DamAssetRecord> catalog = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> writtenBack = new ConcurrentHashMap<>();
    private final Map<String, Path> binaries = new ConcurrentHashMap<>();

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
        loadImageFolder(properties.mockImageDir(), properties.mockDamBaseUrl());
        log.info("Mock DAM initialised with {} assets ({} image files)", catalog.size(), binaries.size());
    }

    /** Adds every image in {@code dir} as an asset {@code DA-3xxxxx}, numbered in filename order. */
    private void loadImageFolder(String dir, String baseUrl) {
        if (dir == null || dir.isBlank()) {
            return;
        }
        Path folder = Path.of(dir).toAbsolutePath().normalize();
        if (!Files.isDirectory(folder)) {
            log.info("Mock DAM image folder {} not found, skipping", folder);
            return;
        }
        List<Path> images;
        try (Stream<Path> files = Files.list(folder)) {
            images = files.filter(Files::isRegularFile)
                    .filter(file -> imageType(file) != null)
                    .sorted(Comparator.comparing((Path file) -> file.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER).thenComparing(file -> file.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.warn("Cannot read mock DAM image folder {}: {}", folder, e.getMessage());
            return;
        }
        for (int i = 0; i < images.size(); i++) {
            Path image = images.get(i);
            String id = "DA-%06d".formatted(IMAGE_ID_BASE + i + 1);
            String filename = image.getFileName().toString();
            catalog.put(id, new DamAssetRecord(id, filename, imageType(image), renditionUrl(baseUrl, id, filename),
                    Map.of("source", folder.getFileName().toString())));
            binaries.put(id, image);
        }
    }

    private static String imageType(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? null : IMAGE_TYPES.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Image file backing a folder-sourced asset; empty for catalog assets (which get generated previews). */
    Optional<Path> binary(String externalAssetId) {
        return Optional.ofNullable(binaries.get(externalAssetId));
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

package com.example.dam.asset.infrastructure.dam;

import com.example.dam.asset.application.port.DamClient.DamAssetRecord;
import com.example.dam.common.domain.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rendition endpoint of the mock DAM (stand-in for the enterprise DAM's preview service). Asset URLs of mock assets
 * point here, so clients can display previews while this application never stores binaries. Assets sourced from the
 * mock image folder are served as their original image; catalog assets get a generated SVG placeholder.
 */
@Hidden
@RestController
@ConditionalOnBooleanProperty(name = "dam.integration.mock", matchIfMissing = true)
class MockDamRenditionController {

    private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml");

    private final MockDamClient damClient;
    private final MockRenditionRenderer renderer = new MockRenditionRenderer();

    MockDamRenditionController(MockDamClient damClient) {
        this.damClient = damClient;
    }

    @GetMapping("/mock-dam/assets/{externalAssetId}/{filename}")
    ResponseEntity<Resource> rendition(@PathVariable String externalAssetId, @PathVariable String filename) {
        DamAssetRecord asset = damClient.getAsset(externalAssetId)
                .filter(record -> record.filename().equals(filename))
                .orElseThrow(() -> notFound(externalAssetId, filename));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .header("X-Content-Type-Options", "nosniff");
        Path image = damClient.binary(externalAssetId).orElse(null);
        if (image != null) {
            if (!Files.isReadable(image)) {
                throw notFound(externalAssetId, filename);
            }
            return response.contentType(MediaType.parseMediaType(asset.mimeType())).body(new FileSystemResource(image));
        }
        return response.contentType(SVG)
                .body(new ByteArrayResource(renderer.render(asset).getBytes(StandardCharsets.UTF_8)));
    }

    private static ResourceNotFoundException notFound(String externalAssetId, String filename) {
        return new ResourceNotFoundException("DAM_ASSET_NOT_FOUND",
                "No mock DAM asset %s/%s".formatted(externalAssetId, filename));
    }
}

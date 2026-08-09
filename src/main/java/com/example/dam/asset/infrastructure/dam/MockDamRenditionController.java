package com.example.dam.asset.infrastructure.dam;

import com.example.dam.asset.application.port.DamClient.DamAssetRecord;
import com.example.dam.common.domain.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rendition endpoint of the mock DAM (stand-in for the enterprise DAM's preview service). Asset URLs of mock assets
 * point here, so clients can display previews while this application never stores binaries.
 */
@Hidden
@RestController
class MockDamRenditionController {

    private static final MediaType SVG = MediaType.parseMediaType("image/svg+xml");

    private final MockDamClient damClient;
    private final MockRenditionRenderer renderer = new MockRenditionRenderer();

    MockDamRenditionController(MockDamClient damClient) {
        this.damClient = damClient;
    }

    @GetMapping("/mock-dam/assets/{externalAssetId}/{filename}")
    ResponseEntity<String> rendition(@PathVariable String externalAssetId, @PathVariable String filename) {
        DamAssetRecord asset = damClient.getAsset(externalAssetId)
                .filter(record -> record.filename().equals(filename))
                .orElseThrow(() -> new ResourceNotFoundException("DAM_ASSET_NOT_FOUND",
                        "No mock DAM asset %s/%s".formatted(externalAssetId, filename)));
        return ResponseEntity.ok()
                .contentType(SVG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(renderer.render(asset));
    }
}

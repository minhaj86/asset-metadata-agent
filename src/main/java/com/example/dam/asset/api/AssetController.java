package com.example.dam.asset.api;

import com.example.dam.asset.application.AssetService;
import com.example.dam.asset.application.AssetService.DamAssetView;
import com.example.dam.asset.application.AssetService.RegisterAssetCommand;
import com.example.dam.asset.application.port.DamClient.DamAssetRecord;
import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.common.domain.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "Assets", description = "Asset references (binaries stay in the external DAM)")
class AssetController {

    private final AssetService assetService;

    AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping("/assets")
    @Operation(summary = "Register an asset reference, optionally with initial metadata")
    ResponseEntity<AssetResponse> register(@Valid @RequestBody RegisterAssetRequest request,
                                           @Parameter(hidden = true) Actor actor) {
        Asset asset = assetService.register(new RegisterAssetCommand(
                request.externalAssetId(), request.filename(), request.mimeType(),
                request.assetUrl() == null ? null : URI.create(request.assetUrl()), request.type(),
                request.metadata() == null ? Map.of() : request.metadata(), request.metadataSource()), actor);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(asset.id()).toUri();
        return ResponseEntity.created(location).body(AssetResponse.from(asset));
    }

    @GetMapping("/assets/{id}")
    @Operation(summary = "Get an asset reference")
    AssetResponse get(@PathVariable UUID id) {
        return AssetResponse.from(assetService.get(id));
    }

    @GetMapping("/assets")
    @Operation(summary = "List asset references")
    List<AssetResponse> list(@RequestParam(required = false) AssetType type,
                             @RequestParam(defaultValue = "0") @Min(0) int page,
                             @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return assetService.list(type, page, size).stream().map(AssetResponse::from).toList();
    }

    @GetMapping("/dam/assets")
    @Operation(summary = "Browse the external DAM (e.g. to find assets to register)")
    List<DamAssetResponse> browseDam(@RequestParam(defaultValue = "") String q,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return assetService.browseDam(q, limit).stream().map(DamAssetResponse::from).toList();
    }

    record DamAssetResponse(String externalAssetId, String filename, String mimeType, String url, boolean registered) {
        static DamAssetResponse from(DamAssetView view) {
            DamAssetRecord record = view.record();
            return new DamAssetResponse(record.externalAssetId(), record.filename(), record.mimeType(),
                    record.url().toString(), view.registered());
        }
    }
}

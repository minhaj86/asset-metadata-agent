package com.example.dam.metadata.api;

import com.example.dam.common.domain.Actor;
import com.example.dam.metadata.application.AssetRevalidationService;
import com.example.dam.metadata.application.AssetRevalidationService.Revalidation;
import com.example.dam.metadata.application.MetadataService;
import com.example.dam.metadata.application.enrichment.EnrichmentRun;
import com.example.dam.metadata.application.enrichment.MetadataEnrichmentService;
import com.example.dam.metadata.application.quality.MetadataQualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets/{assetId}")
@Tag(name = "Asset metadata", description = "Metadata CRUD, validation, quality and AI enrichment of one asset")
class AssetMetadataController {

    private final MetadataService metadataService;
    private final AssetRevalidationService revalidationService;
    private final MetadataQualityService qualityService;
    private final MetadataEnrichmentService enrichmentService;

    AssetMetadataController(MetadataService metadataService, AssetRevalidationService revalidationService,
                            MetadataQualityService qualityService, MetadataEnrichmentService enrichmentService) {
        this.metadataService = metadataService;
        this.revalidationService = revalidationService;
        this.qualityService = qualityService;
        this.enrichmentService = enrichmentService;
    }

    @GetMapping("/metadata")
    @Operation(summary = "Current metadata with provenance (source, status, confidence)")
    AssetMetadataResponse getMetadata(@PathVariable UUID assetId) {
        return AssetMetadataResponse.from(metadataService.getMetadata(assetId));
    }

    @PutMapping("/metadata/{field}")
    @Operation(summary = "Set a metadata value manually (validated against the schema and audited)")
    MetadataEntryResponse setValue(@PathVariable UUID assetId, @PathVariable String field,
                                   @Valid @RequestBody SetMetadataValueRequest request,
                                   @Parameter(hidden = true) Actor actor) {
        return MetadataEntryResponse.from(metadataService.setValue(assetId, field, request.value(), request.reason(), actor));
    }

    @DeleteMapping("/metadata/{field}")
    @Operation(summary = "Remove a metadata value (audited)")
    ResponseEntity<Void> removeValue(@PathVariable UUID assetId, @PathVariable String field,
                                     @RequestParam(required = false) String reason,
                                     @Parameter(hidden = true) Actor actor) {
        metadataService.removeValue(assetId, field, reason, actor);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validate")
    @Operation(summary = "Deterministic schema validation; refreshes the derived asset status")
    ValidationResponse validate(@PathVariable UUID assetId) {
        Revalidation revalidation = revalidationService.revalidate(assetId);
        return ValidationResponse.from(revalidation.validation(), revalidation.status());
    }

    @GetMapping("/quality")
    @Operation(summary = "Quality report: deterministic score and findings plus optional AI suggestions")
    QualityReportResponse quality(@PathVariable UUID assetId,
                                  @RequestParam(defaultValue = "true") boolean includeAi) {
        return QualityReportResponse.from(qualityService.assess(assetId, includeAi));
    }

    @PostMapping("/enrich")
    @Operation(summary = "Run the agentic enrichment workflow synchronously; proposals are routed by deterministic policy")
    EnrichmentResponse enrich(@PathVariable UUID assetId, @Parameter(hidden = true) Actor actor) {
        return EnrichmentResponse.from(enrichmentService.enrich(assetId, actor));
    }

    @PostMapping(value = "/enrich", headers = "Prefer=respond-async")
    @Operation(summary = "Start the enrichment workflow in the background (Prefer: respond-async)",
            description = "Returns 202 with the tracked run; follow it via GET /api/workflows/{workflowId} or its "
                    + "server-sent events at /api/workflows/{workflowId}/events")
    ResponseEntity<WorkflowRunResponse> startEnrichment(@PathVariable UUID assetId,
                                                        @Parameter(hidden = true) Actor actor) {
        EnrichmentRun run = enrichmentService.start(assetId, actor);
        return ResponseEntity.accepted()
                .location(URI.create("/api/workflows/" + run.id()))
                .header("Preference-Applied", "respond-async")
                .body(WorkflowRunResponse.from(run));
    }
}

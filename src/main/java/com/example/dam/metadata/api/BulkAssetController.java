package com.example.dam.metadata.api;

import com.example.dam.asset.domain.AssetStatus;
import com.example.dam.common.domain.Actor;
import com.example.dam.common.domain.DamException;
import com.example.dam.common.domain.InvalidRequestException;
import com.example.dam.metadata.application.AssetRevalidationService;
import com.example.dam.metadata.application.AssetRevalidationService.Revalidation;
import com.example.dam.metadata.application.catalog.MetadataExportService;
import com.example.dam.metadata.application.catalog.MetadataExportService.AssetExport;
import com.example.dam.metadata.application.enrichment.MetadataEnrichmentService;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.value.MetadataValue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Bulk operations on selected assets. Each operation uses the same services and rules as the single-asset API. */
@RestController
@RequestMapping("/api/assets")
@Tag(name = "Bulk operations", description = "Validate, enrich or export several assets")
class BulkAssetController {

    private static final int MAX_VALIDATE = 200;

    private final AssetRevalidationService revalidationService;
    private final MetadataEnrichmentService enrichmentService;
    private final MetadataExportService exportService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    BulkAssetController(AssetRevalidationService revalidationService, MetadataEnrichmentService enrichmentService,
                        MetadataExportService exportService, JsonMapper jsonMapper, Clock clock) {
        this.revalidationService = revalidationService;
        this.enrichmentService = enrichmentService;
        this.exportService = exportService;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @PostMapping("/validate")
    @Operation(summary = "Deterministic validation of several assets; refreshes status and summaries")
    List<BulkValidationItem> validate(@Valid @RequestBody AssetSelection selection) {
        if (selection.assetIds().size() > MAX_VALIDATE) {
            throw new InvalidRequestException("BULK_LIMIT_EXCEEDED", "At most " + MAX_VALIDATE + " assets per request");
        }
        List<BulkValidationItem> results = new ArrayList<>();
        for (UUID assetId : new LinkedHashSet<>(selection.assetIds())) {
            try {
                Revalidation revalidation = revalidationService.revalidate(assetId);
                var validation = revalidation.validation();
                results.add(new BulkValidationItem(assetId, validation.asset().externalAssetId(),
                        validation.report().isValid(), revalidation.status(), validation.report().errors().size(),
                        validation.report().warnings().size(), validation.completeness().overallPercentage(),
                        validation.completeness().requiredPercentage(), null));
            } catch (DamException e) {
                results.add(new BulkValidationItem(assetId, null, false, null, 0, 0, 0, 0,
                        new WorkflowRunResponse.RunErrorResponse(e.code(), e.getMessage())));
            }
        }
        return results;
    }

    @PostMapping("/enrich")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Start background enrichment for several assets",
            description = "Creates proposals only; the deterministic approval policy decides what is applied and "
                    + "sensitive fields always wait for a human decision")
    List<WorkflowRunResponse> enrich(@Valid @RequestBody AssetSelection selection,
                                     @Parameter(hidden = true) Actor actor) {
        return enrichmentService.startAll(selection.assetIds(), actor).stream().map(WorkflowRunResponse::from).toList();
    }

    @PostMapping("/export")
    @Operation(summary = "Export current metadata with provenance as CSV (one row per value) or JSON")
    ResponseEntity<byte[]> export(@Valid @RequestBody ExportRequest request) {
        List<AssetExport> exports = exportService.export(request.assetIds());
        boolean json = "json".equalsIgnoreCase(request.format());
        if (!json && request.format() != null && !"csv".equalsIgnoreCase(request.format())) {
            throw new InvalidRequestException("UNSUPPORTED_FORMAT", "format must be csv or json");
        }
        byte[] body = json ? jsonMapper.writeValueAsBytes(exports.stream().map(BulkAssetController::toJson).toList())
                : toCsv(exports).getBytes(StandardCharsets.UTF_8);
        String filename = "metadata-export-" + LocalDate.now(clock) + (json ? ".json" : ".csv");
        return ResponseEntity.ok()
                .contentType(json ? MediaType.APPLICATION_JSON : new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build()
                        .toString())
                .body(body);
    }

    private static Map<String, Object> toJson(AssetExport export) {
        return Map.of(
                "assetId", export.asset().id(),
                "externalAssetId", export.asset().externalAssetId(),
                "assetType", export.asset().type(),
                "assetStatus", export.asset().status(),
                "metadata", export.metadata().stream().map(MetadataEntryResponse::from).toList());
    }

    private static String toCsv(List<AssetExport> exports) {
        StringBuilder csv = new StringBuilder(
                "externalAssetId,assetType,field,value,source,status,confidence,aiModel,updatedAt,updatedBy\r\n");
        for (AssetExport export : exports) {
            for (AssetMetadata entry : export.metadata()) {
                csv.append(String.join(",", List.of(
                        csvCell(export.asset().externalAssetId()),
                        csvCell(export.asset().type().name()),
                        csvCell(entry.fieldKey()),
                        csvCell(display(entry.value())),
                        csvCell(entry.source().name()),
                        csvCell(entry.status().name()),
                        csvCell(entry.confidence() == null ? "" : String.format(Locale.ROOT, "%.3f", entry.confidence())),
                        csvCell(entry.aiModel() == null ? "" : entry.aiModel()),
                        csvCell(entry.updatedAt().toString()),
                        csvCell(entry.updatedBy())))).append("\r\n");
            }
        }
        return csv.toString();
    }

    private static String display(MetadataValue value) {
        return value.display();
    }

    /** RFC 4180 quoting; cells starting with formula characters are prefixed to prevent CSV injection. */
    private static String csvCell(String value) {
        String safe = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")
                ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

    record AssetSelection(@NotEmpty @Size(max = 500) List<UUID> assetIds) {
    }

    record ExportRequest(@NotEmpty @Size(max = MetadataExportService.MAX_ASSETS) List<UUID> assetIds, String format) {
    }

    record BulkValidationItem(UUID assetId, String externalAssetId, boolean valid, AssetStatus assetStatus,
                              int errorCount, int warningCount, double completenessPercentage,
                              double requiredCompletenessPercentage, WorkflowRunResponse.RunErrorResponse error) {
    }
}

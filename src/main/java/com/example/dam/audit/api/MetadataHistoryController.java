package com.example.dam.audit.api;

import com.example.dam.audit.application.MetadataAuditService;
import com.example.dam.audit.domain.AuditQuery;
import com.example.dam.audit.domain.AuditQuery.ActorType;
import com.example.dam.audit.domain.MetadataAuditRepository;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.audit.AuditAction;
import com.example.dam.metadata.domain.audit.MetadataAuditEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Audit", description = "Immutable history of metadata changes and approval decisions")
class MetadataHistoryController {

    private final MetadataAuditService auditService;

    MetadataHistoryController(MetadataAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/api/assets/{assetId}/metadata/history")
    @Operation(summary = "Metadata audit history of an asset, newest first")
    List<AuditEventResponse> history(@PathVariable UUID assetId,
                                     @RequestParam(required = false) String field,
                                     @RequestParam(defaultValue = "200") @Min(1) @Max(1000) int limit) {
        return auditService.history(assetId, field, limit).stream()
                .map(event -> AuditEventResponse.from(event, null))
                .toList();
    }

    @GetMapping("/api/audit/events")
    @Operation(summary = "Global audit log with filters, newest first")
    AuditPageResponse events(
            @RequestParam(required = false) UUID assetId,
            @Parameter(description = "Substring of the external asset id") @RequestParam(required = false) String asset,
            @RequestParam(required = false) String field,
            @RequestParam(required = false) MetadataSource source,
            @Parameter(description = "Exact actor id, e.g. maria.reviewer or ai:metadataEnrichmentAgent")
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) ActorType actorType,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return AuditPageResponse.from(auditService.search(
                new AuditQuery(assetId, asset, field, source, actor, actorType, action, from, to, page, size)));
    }

    record AuditEventResponse(UUID id, UUID assetId, String externalAssetId, String field, AuditAction action,
                              Object previousValue, Object newValue, MetadataSource source, String aiModel,
                              Double confidence, String actor, String approvedBy, UUID proposalId, String reason,
                              String correlationId, Instant timestamp) {
        static AuditEventResponse from(MetadataAuditEvent event, String externalAssetId) {
            return new AuditEventResponse(event.id(), event.assetId(), externalAssetId, event.field(), event.action(),
                    event.previousValue() == null ? null : event.previousValue().toPlainValue(),
                    event.newValue() == null ? null : event.newValue().toPlainValue(),
                    event.source(), event.aiModel(), event.confidence(), event.actor(), event.approvedBy(),
                    event.proposalId(), event.reason(), event.correlationId(), event.timestamp());
        }
    }

    record AuditPageResponse(List<AuditEventResponse> items, int page, int size, long totalItems, int totalPages) {
        static AuditPageResponse from(MetadataAuditRepository.Page page) {
            return new AuditPageResponse(
                    page.items().stream().map(entry -> AuditEventResponse.from(entry.event(), entry.externalAssetId()))
                            .toList(),
                    page.page(), page.size(), page.totalItems(),
                    (int) Math.ceil(page.totalItems() / (double) page.size()));
        }
    }
}

package com.example.dam.metadata.api;

import com.example.dam.common.domain.ResourceNotFoundException;
import com.example.dam.metadata.application.enrichment.EnrichmentRun;
import com.example.dam.metadata.application.enrichment.EnrichmentRun.RunStatus;
import com.example.dam.metadata.application.enrichment.EnrichmentRunTracker;
import com.example.dam.metadata.application.enrichment.EnrichmentRunTracker.Subscription;
import com.example.dam.metadata.application.enrichment.MetadataEnrichmentService;
import com.example.dam.metadata.application.enrichment.WorkflowStageDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/workflows")
@Validated
@Tag(name = "Workflows", description = "Tracked agentic enrichment runs with live stage progress")
class WorkflowRunController {

    static final String EVENT_NAME = "workflow";
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(15);

    private final EnrichmentRunTracker tracker;
    private final MetadataEnrichmentService enrichmentService;

    WorkflowRunController(EnrichmentRunTracker tracker, MetadataEnrichmentService enrichmentService) {
        this.tracker = tracker;
        this.enrichmentService = enrichmentService;
    }

    @GetMapping
    @Operation(summary = "Recent workflow runs, newest first")
    List<WorkflowRunResponse> recent(@RequestParam(required = false) UUID assetId,
                                     @RequestParam(required = false) RunStatus status,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        return tracker.recent(assetId, status, limit).stream().map(WorkflowRunResponse::from).toList();
    }

    @GetMapping("/plan")
    @Operation(summary = "Planned stages of the enrichment workflow (topology)")
    List<WorkflowStageDefinition> plan() {
        return enrichmentService.plan();
    }

    @GetMapping("/{workflowId}")
    @Operation(summary = "Status and per-stage progress of one run")
    WorkflowRunResponse get(@PathVariable UUID workflowId) {
        return WorkflowRunResponse.from(tracker.find(workflowId).orElseThrow(() -> notFound(workflowId)));
    }

    @GetMapping(value = "/{workflowId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Server-sent events: a full run snapshot on every stage change until the run finishes")
    SseEmitter events(@PathVariable UUID workflowId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        Subscription created = tracker.subscribe(workflowId, run -> send(emitter, run))
                .orElseThrow(() -> notFound(workflowId));
        subscription.set(created);
        Runnable close = () -> subscription.get().close();
        emitter.onCompletion(close);
        emitter.onTimeout(close);
        emitter.onError(error -> close.run());
        return emitter;
    }

    private static void send(SseEmitter emitter, EnrichmentRun run) {
        try {
            emitter.send(SseEmitter.event().name(EVENT_NAME).id(run.id() + ":" + run.status())
                    .data(WorkflowRunResponse.from(run), MediaType.APPLICATION_JSON));
            if (run.status().isTerminal()) {
                emitter.complete();
            }
        } catch (IOException | IllegalStateException e) {
            // Client went away; the failing subscriber is removed by the tracker.
            throw new IllegalStateException("SSE client disconnected", e);
        }
    }

    private static ResourceNotFoundException notFound(UUID workflowId) {
        return new ResourceNotFoundException("WORKFLOW_NOT_FOUND", "Workflow run %s not found".formatted(workflowId));
    }
}

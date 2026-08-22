package com.example.dam.metadata.api;

import com.example.dam.common.domain.Actor;
import com.example.dam.common.domain.InvalidRequestException;
import com.example.dam.metadata.application.MetadataApprovalService;
import com.example.dam.metadata.application.review.BulkApprovalService;
import com.example.dam.metadata.application.review.BulkApprovalService.BulkApprovalResult;
import com.example.dam.metadata.application.review.BulkApprovalService.ItemResult;
import com.example.dam.metadata.application.review.BulkApprovalService.Outcome;
import com.example.dam.metadata.application.review.ProposalReviewQueryService;
import com.example.dam.metadata.domain.MetadataStatus;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import com.example.dam.metadata.domain.proposal.ProposalQuery;
import com.example.dam.metadata.domain.proposal.ProposalReviewPolicy.RiskLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metadata/proposals")
@Validated
@Tag(name = "Metadata proposals", description = "Human-in-the-loop review of AI proposals")
class MetadataProposalController {

    private static final String ALL_STATUSES = "ALL";

    private final MetadataApprovalService approvalService;
    private final ProposalReviewQueryService queryService;
    private final BulkApprovalService bulkApprovalService;

    MetadataProposalController(MetadataApprovalService approvalService, ProposalReviewQueryService queryService,
                               BulkApprovalService bulkApprovalService) {
        this.approvalService = approvalService;
        this.queryService = queryService;
        this.bulkApprovalService = bulkApprovalService;
    }

    @GetMapping
    @Operation(summary = "List proposals; defaults to the open review queue (status PROPOSED)")
    List<ProposalResponse> list(
            @Parameter(description = "PROPOSED (default), APPROVED, REJECTED or ALL")
            @RequestParam(defaultValue = "PROPOSED") String status,
            @RequestParam(required = false) ApprovalRoute route,
            @RequestParam(required = false) UUID assetId,
            @Parameter(description = "Substring of the external asset id") @RequestParam(required = false) String asset,
            @RequestParam(required = false) UUID workflowRunId,
            @RequestParam(required = false) String field,
            @Parameter(description = "Agent that produced the proposal, e.g. metadataExtractionAgent")
            @RequestParam(required = false) String agent,
            @RequestParam(required = false) @DecimalMin("0.0") @DecimalMax("1.0") Double minConfidence,
            @RequestParam(required = false) @DecimalMin("0.0") @DecimalMax("1.0") Double maxConfidence,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        ProposalQuery query = new ProposalQuery(status(status), route, assetId, asset, workflowRunId, field, agent,
                minConfidence, maxConfidence, createdAfter, limit);
        return queryService.list(query, riskLevel).stream().map(ProposalResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one proposal with confidence, explanation, evidence and review guidance")
    ProposalResponse get(@PathVariable UUID id) {
        return ProposalResponse.from(queryService.get(id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve and apply a proposal, optionally with an edited value (requires X-User-Id; audited)")
    ProposalResponse approve(@PathVariable UUID id, @Valid @RequestBody(required = false) ApproveRequest request,
                             @Parameter(hidden = true) Actor actor) {
        return ProposalResponse.from(queryService.view(approvalService.approve(id, actor,
                request == null ? null : request.comment(), request == null ? null : request.value())));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a proposal (requires X-User-Id and a reason; audited)")
    ProposalResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request,
                            @Parameter(hidden = true) Actor actor) {
        return ProposalResponse.from(queryService.view(approvalService.reject(id, actor, request.reason())));
    }

    @PostMapping("/bulk-approve")
    @Operation(summary = "Approve several proposals; only those the backend policy marks bulk-approvable are applied")
    BulkApprovalResponse bulkApprove(@Valid @RequestBody BulkApproveRequest request,
                                     @Parameter(hidden = true) Actor actor) {
        return BulkApprovalResponse.from(bulkApprovalService.approve(request.proposalIds(), actor, request.comment()));
    }

    private static MetadataStatus status(String status) {
        if (ALL_STATUSES.equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return MetadataStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("INVALID_STATUS",
                    "Unknown proposal status '%s'; use PROPOSED, APPROVED, REJECTED or ALL".formatted(status));
        }
    }

    record ApproveRequest(
            @Size(max = 1000) String comment,
            @Schema(description = "Reviewer's edited value; omit to apply the proposed value as is") Object value) {
    }

    record RejectRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    record BulkApproveRequest(@NotEmpty @Size(max = BulkApprovalService.MAX_BATCH_SIZE) List<UUID> proposalIds,
                              @Size(max = 1000) String comment) {
    }

    record BulkApprovalResponse(long approved, long skipped, long failed, List<ItemResult> items) {
        static BulkApprovalResponse from(BulkApprovalResult result) {
            return new BulkApprovalResponse(result.count(Outcome.APPROVED), result.count(Outcome.SKIPPED),
                    result.count(Outcome.FAILED), result.items());
        }
    }
}

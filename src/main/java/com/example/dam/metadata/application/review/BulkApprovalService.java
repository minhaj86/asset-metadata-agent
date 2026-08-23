package com.example.dam.metadata.application.review;

import com.example.dam.common.domain.Actor;
import com.example.dam.common.domain.DamException;
import com.example.dam.common.domain.InvalidRequestException;
import com.example.dam.metadata.application.MetadataApprovalService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Approves several proposals at once, but only those the deterministic review policy marks as bulk-approvable
 * (low-risk proposals for fields the schema allows to be automated). Everything else is skipped and must be
 * reviewed individually. Each approval runs in its own transaction so one failure does not affect the others.
 */
@Service
public class BulkApprovalService {

    public static final int MAX_BATCH_SIZE = 100;

    private final ProposalReviewQueryService queryService;
    private final MetadataApprovalService approvalService;

    public BulkApprovalService(ProposalReviewQueryService queryService, MetadataApprovalService approvalService) {
        this.queryService = queryService;
        this.approvalService = approvalService;
    }

    public BulkApprovalResult approve(List<UUID> proposalIds, Actor reviewer, String comment) {
        MetadataApprovalService.requireHumanReviewer(reviewer);
        if (proposalIds.size() > MAX_BATCH_SIZE) {
            throw new InvalidRequestException("BULK_LIMIT_EXCEEDED",
                    "At most " + MAX_BATCH_SIZE + " proposals per bulk approval");
        }
        List<ItemResult> results = new ArrayList<>();
        for (UUID proposalId : new LinkedHashSet<>(proposalIds)) {
            results.add(approveOne(proposalId, reviewer, comment));
        }
        return new BulkApprovalResult(results);
    }

    private ItemResult approveOne(UUID proposalId, Actor reviewer, String comment) {
        try {
            ProposalView view = queryService.get(proposalId);
            if (!view.guidance().bulkApprovable()) {
                return new ItemResult(proposalId, view.proposal().fieldKey(), Outcome.SKIPPED, "NOT_BULK_APPROVABLE",
                        "Requires individual review: " + String.join("; ", view.guidance().riskFactors().isEmpty()
                                ? List.of("not eligible for bulk approval") : view.guidance().riskFactors()));
            }
            approvalService.approve(proposalId, reviewer, comment);
            return new ItemResult(proposalId, view.proposal().fieldKey(), Outcome.APPROVED, null, null);
        } catch (DamException e) {
            return new ItemResult(proposalId, null, Outcome.FAILED, e.code(), e.getMessage());
        }
    }

    public enum Outcome { APPROVED, SKIPPED, FAILED }

    public record ItemResult(UUID proposalId, String field, Outcome outcome, String code, String message) {
    }

    public record BulkApprovalResult(List<ItemResult> items) {
        public BulkApprovalResult {
            items = List.copyOf(items);
        }

        public long count(Outcome outcome) {
            return items.stream().filter(item -> item.outcome() == outcome).count();
        }
    }
}

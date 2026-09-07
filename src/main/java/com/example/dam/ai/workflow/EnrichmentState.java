package com.example.dam.ai.workflow;

import com.example.dam.ai.agent.EnrichmentOutput;
import com.example.dam.ai.agent.ExtractionOutput;
import com.example.dam.ai.agent.QualityReviewOutput;
import com.example.dam.ai.agent.TaxonomyClassification;
import com.example.dam.metadata.application.enrichment.AssetEnrichmentContext;
import dev.langchain4j.agentic.declarative.TypedKey;

/**
 * Shared state of the enrichment workflow (the LangChain4j {@code AgenticScope}). Typed keys carry Java objects
 * between steps; string keys carry the JSON renderings used in prompts.
 */
public final class EnrichmentState {

    public static final String ASSET_ID = "assetId";
    public static final String WORKFLOW_RUN_ID = "workflowRunId";
    public static final String ASSET_CONTEXT = "assetContext";
    public static final String CURRENT_METADATA = "currentMetadata";
    public static final String FIELD_CATALOG = "fieldCatalog";
    public static final String TAXONOMY_CANDIDATES = "taxonomyCandidates";
    public static final String GOVERNANCE_GUIDANCE = "governanceGuidance";
    public static final String WORKING_METADATA = "workingMetadata";
    public static final String METADATA_TO_REVIEW = "metadataToReview";
    public static final String DETERMINISTIC_FINDINGS = "deterministicFindings";
    public static final String AUTO_APPROVAL_NOTE = "autoApprovalNote";
    public static final String HUMAN_REVIEW_NOTE = "humanReviewNote";

    private EnrichmentState() {
    }

    public static final class Context implements TypedKey<AssetEnrichmentContext> {
        @Override
        public String name() {
            return "enrichmentContext";
        }
    }

    public static final class Extraction implements TypedKey<ExtractionOutput> {
        @Override
        public String name() {
            return "extraction";
        }
    }

    public static final class Taxonomy implements TypedKey<TaxonomyClassification> {
        @Override
        public String name() {
            return "taxonomy";
        }
    }

    public static final class Enrichment implements TypedKey<EnrichmentOutput> {
        @Override
        public String name() {
            return "enrichment";
        }
    }

    public static final class Candidates implements TypedKey<CandidateSet> {
        @Override
        public String name() {
            return "candidates";
        }
    }

    public static final class QualityReview implements TypedKey<QualityReviewOutput> {
        @Override
        public String name() {
            return "qualityReview";
        }
    }

    public static final class Plan implements TypedKey<ApprovalPlan> {
        @Override
        public String name() {
            return "approvalPlan";
        }
    }
}

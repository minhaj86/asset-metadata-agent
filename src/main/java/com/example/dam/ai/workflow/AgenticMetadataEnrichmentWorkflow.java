package com.example.dam.ai.workflow;

import com.example.dam.ai.agent.AgentFactory;
import com.example.dam.ai.agent.MetadataEnrichmentAgent;
import com.example.dam.ai.agent.MetadataExtractionAgent;
import com.example.dam.ai.agent.MetadataQualityAgent;
import com.example.dam.ai.agent.MetadataSupervisorAgent;
import com.example.dam.ai.agent.PromptContextRenderer;
import com.example.dam.ai.agent.TaxonomyAgent;
import com.example.dam.ai.config.AiModelInfo;
import com.example.dam.ai.config.AiProperties;
import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.ai.observability.AgentObservabilityListener;
import com.example.dam.ai.observability.WorkflowTrace;
import com.example.dam.ai.rag.MetadataKnowledgeService;
import com.example.dam.ai.workflow.step.ApprovalDecisionStep;
import com.example.dam.ai.workflow.step.AutoApprovalRoutingStep;
import com.example.dam.ai.workflow.step.ConsolidateProposalsStep;
import com.example.dam.ai.workflow.step.HumanReviewRoutingStep;
import com.example.dam.ai.workflow.step.LoadAssetContextStep;
import com.example.dam.ai.workflow.step.MergeWithExistingMetadataStep;
import com.example.dam.common.domain.CapabilityUnavailableException;
import com.example.dam.common.domain.DamException;
import com.example.dam.common.observability.CorrelationContext;
import com.example.dam.common.observability.MdcPropagatingExecutor;
import com.example.dam.metadata.application.MetadataValidationService;
import com.example.dam.metadata.application.enrichment.AgentStepTrace.StepKind;
import com.example.dam.metadata.application.enrichment.AssetEnrichmentContext;
import com.example.dam.metadata.application.enrichment.EnrichmentContextAssembler;
import com.example.dam.metadata.application.enrichment.EnrichmentProgressListener;
import com.example.dam.metadata.application.enrichment.MetadataEnrichmentResult;
import com.example.dam.metadata.application.enrichment.MetadataEnrichmentWorkflow;
import com.example.dam.metadata.application.enrichment.WorkflowStageDefinition;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.proposal.ApprovalPolicy;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import com.example.dam.metadata.domain.schema.WellKnownFields;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * LangChain4j Agentic implementation of the {@link MetadataEnrichmentWorkflow} port.
 *
 * <pre>
 * metadataSupervisor (sequence)
 *   loadAssetContext            deterministic: asset, metadata, schema, DAM, PIM, vision, RAG guidance
 *   metadataExtractionAgent     AI
 *   mergeWithExistingMetadata   deterministic: working view = existing + extracted
 *   parallelReasoning (parallel)
 *     taxonomyBranch (conditional: classifiable asset without approved taxonomy) -> taxonomyAgent  AI + tool
 *     metadataEnrichmentAgent   AI + RAG tool
 *   consolidateProposals        deterministic: typed, schema-checked, invented taxonomy rejected
 *   qualityReviewBranch (conditional: something to review) -> metadataQualityAgent  AI
 *   approvalDecision            deterministic approval policy
 *   approvalRouting (conditional) -> autoApprovalRouting | humanReviewRouting
 * </pre>
 * All steps share one {@code AgenticScope}. The workflow returns proposals only; persistence happens in the
 * application layer. The framework API is isolated in this package so that its evolution does not leak.
 */
@Component
@ConditionalOnAiEnabled
class AgenticMetadataEnrichmentWorkflow implements MetadataEnrichmentWorkflow, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AgenticMetadataEnrichmentWorkflow.class);
    private static final String TAXONOMY_AGENT = "taxonomyAgent";
    private static final String QUALITY_AGENT = "metadataQualityAgent";
    private static final String PARALLEL_REASONING = "parallelReasoning";

    /** Published topology; ids match the agent names reported by the observability listener. */
    private static final List<WorkflowStageDefinition> STAGES = List.of(
            stage("loadAssetContext", "Load asset context", StepKind.DETERMINISTIC_STEP,
                    "Asset, metadata, schema, DAM embedded metadata, product data, image analysis and governance rules",
                    null, false),
            stage(CandidateConsolidator.EXTRACTION_AGENT, "Metadata Extraction Agent", StepKind.AI_AGENT,
                    "Extracts values supported by filename, embedded metadata, image analysis and product data",
                    null, false),
            stage("mergeWithExistingMetadata", "Merge with existing metadata", StepKind.DETERMINISTIC_STEP,
                    "Builds the working view of existing and extracted metadata", null, false),
            stage(TAXONOMY_AGENT, "Taxonomy Agent", StepKind.AI_AGENT,
                    "Maps the asset to an existing taxonomy node retrieved with the taxonomy search tool",
                    PARALLEL_REASONING, true),
            stage(CandidateConsolidator.ENRICHMENT_AGENT, "Metadata Enrichment Agent", StepKind.AI_AGENT,
                    "Proposes descriptive metadata using product information and metadata guidelines (RAG)",
                    PARALLEL_REASONING, false),
            stage("consolidateProposals", "Consolidate & validate proposals", StepKind.DETERMINISTIC_STEP,
                    "Types, validates and de-duplicates suggestions; rejects invented taxonomy nodes", null, false),
            stage(QUALITY_AGENT, "Metadata Quality Agent", StepKind.AI_AGENT,
                    "Reviews soft quality criteria of the combined metadata", null, true),
            stage("approvalDecision", "Approval evaluation", StepKind.DETERMINISTIC_STEP,
                    "Plans the route of every proposal with the deterministic approval policy", null, false),
            stage("autoApprovalRouting", "Auto-approval routing", StepKind.DETERMINISTIC_STEP,
                    "Proposals the policy allows to apply automatically", null, true),
            stage("humanReviewRouting", "Human review routing", StepKind.DETERMINISTIC_STEP,
                    "Proposals that need a human decision", null, true));

    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
    private final MdcPropagatingExecutor executor = new MdcPropagatingExecutor(virtualThreads);
    private final MetadataSupervisorAgent supervisor;
    private final AiProperties properties;
    private final AiModelInfo modelInfo;
    private final MeterRegistry meterRegistry;

    AgenticMetadataEnrichmentWorkflow(AgentFactory agents, EnrichmentContextAssembler assembler,
                                      MetadataKnowledgeService knowledgeService,
                                      MetadataValidationService validationService, PromptContextRenderer renderer,
                                      CandidateConsolidator consolidator, ApprovalPolicy approvalPolicy,
                                      AgentObservabilityListener listener, EnrichmentProgressListener progress,
                                      AiProperties properties, AiModelInfo modelInfo, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.modelInfo = modelInfo;
        this.meterRegistry = meterRegistry;

        MetadataExtractionAgent extraction = agents.extractionAgent(EnrichmentState.Extraction.class);
        TaxonomyAgent taxonomy = agents.taxonomyAgent(EnrichmentState.Taxonomy.class);
        MetadataEnrichmentAgent enrichment = agents.enrichmentAgent(EnrichmentState.Enrichment.class);
        MetadataQualityAgent quality = agents.qualityAgent(EnrichmentState.QualityReview.class);

        UntypedAgent taxonomyBranch = AgenticServices.conditionalBuilder()
                .name("taxonomyBranch")
                .subAgents("asset is taxonomy-classifiable and has no approved taxonomy path",
                        AgenticMetadataEnrichmentWorkflow::needsTaxonomy, taxonomy)
                .build();
        UntypedAgent parallelReasoning = AgenticServices.parallelBuilder()
                .name(PARALLEL_REASONING)
                .subAgents(taxonomyBranch, enrichment)
                .executor(executor)
                .build();
        UntypedAgent qualityBranch = AgenticServices.conditionalBuilder()
                .name("qualityReviewBranch")
                .subAgents("there is metadata to review", AgenticMetadataEnrichmentWorkflow::hasMetadataToReview, quality)
                .build();
        UntypedAgent approvalRouting = AgenticServices.conditionalBuilder()
                .name("approvalRouting")
                .subAgents("plan contains auto-approvable proposals",
                        scope -> plan(scope).has(ApprovalRoute.AUTO_APPROVE), new AutoApprovalRoutingStep())
                .subAgents("plan contains proposals needing a human decision",
                        scope -> plan(scope).has(ApprovalRoute.HUMAN_REVIEW, ApprovalRoute.LOW_CONFIDENCE),
                        new HumanReviewRoutingStep())
                .build();

        EnrichmentResultAssembler resultAssembler = new EnrichmentResultAssembler(modelInfo);
        // The pipeline is nested below the typed supervisor on purpose: in langchain4j-agentic 1.21.0-beta31 an
        // inherited listener reaches non-AI steps only through a parent workflow's registerInheritedParentListener.
        UntypedAgent pipeline = AgenticServices.sequenceBuilder()
                .name("enrichmentPipeline")
                .subAgents(
                        new LoadAssetContextStep(assembler, knowledgeService, validationService, renderer),
                        extraction,
                        new MergeWithExistingMetadataStep(consolidator, renderer),
                        parallelReasoning,
                        new ConsolidateProposalsStep(consolidator, renderer),
                        qualityBranch,
                        new ApprovalDecisionStep(approvalPolicy),
                        approvalRouting)
                .build();
        this.supervisor = AgenticServices.sequenceBuilder(MetadataSupervisorAgent.class)
                .name("metadataSupervisor")
                .subAgents(pipeline)
                .beforeCall(scope -> {
                    // Invocation arguments are already in the scope here, so the trace can report progress per run.
                    UUID runId = (UUID) scope.readState(EnrichmentState.WORKFLOW_RUN_ID);
                    scope.writeState(AgentObservabilityListener.TRACE_KEY,
                            new WorkflowTrace(step -> progress.stageFinished(runId, step)));
                    CorrelationContext.correlationId()
                            .ifPresent(id -> scope.writeState(AgentObservabilityListener.CORRELATION_KEY, id));
                })
                .errorHandler(new WorkflowErrorHandler())
                .listener(listener)
                .output(resultAssembler::assemble)
                .build();
    }

    @Override
    public MetadataEnrichmentResult enrich(UUID assetId, UUID runId) {
        MDC.put(CorrelationContext.WORKFLOW_RUN_ID, runId.toString());
        MDC.put(CorrelationContext.ASSET_ID, assetId.toString());
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "failure";
        Future<MetadataEnrichmentResult> run = null;
        try {
            log.info("Enrichment workflow {} started for asset {}", runId, assetId);
            run = submit(() -> supervisor.enrich(assetId, runId));
            MetadataEnrichmentResult result = run.get(properties.workflow().timeout().toMillis(), TimeUnit.MILLISECONDS);
            outcome = result.allAiAgentsFailed() ? "degraded" : "success";
            log.info("Enrichment workflow {} finished: {} proposal(s), {} warning(s)", runId, result.proposals().size(),
                    result.warnings().size());
            return result;
        } catch (TimeoutException e) {
            run.cancel(true);
            outcome = "timeout";
            throw new CapabilityUnavailableException("AI_TIMEOUT",
                    "Enrichment did not finish within " + properties.workflow().timeout(), e);
        } catch (ExecutionException e) {
            throw translate(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CapabilityUnavailableException("AI_INTERRUPTED", "Enrichment was interrupted", e);
        } finally {
            sample.stop(Timer.builder("dam.ai.workflow.duration").tag("workflow", "metadata-enrichment")
                    .tag("outcome", outcome).register(meterRegistry));
            MDC.remove(CorrelationContext.WORKFLOW_RUN_ID);
            MDC.remove(CorrelationContext.ASSET_ID);
        }
    }

    @Override
    public List<WorkflowStageDefinition> stages() {
        return STAGES;
    }

    @Override
    public String aiModel() {
        return modelInfo.chatModelLabel();
    }

    @Override
    public Optional<String> unavailableReason() {
        return modelInfo.available() ? Optional.empty() : Optional.ofNullable(modelInfo.unavailableReason());
    }

    private <T> Future<T> submit(Callable<T> task) {
        FutureTask<T> future = new FutureTask<>(task);
        executor.execute(future);
        return future;
    }

    /** Domain failures (e.g. asset not found) pass through; anything else means the AI capability failed. */
    private static RuntimeException translate(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DamException damException) {
                return damException;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        log.error("Enrichment workflow failed", failure);
        return new CapabilityUnavailableException("AI_WORKFLOW_FAILED",
                "Enrichment workflow failed: " + failure.getClass().getSimpleName(), failure);
    }

    private static boolean needsTaxonomy(AgenticScope scope) {
        AssetEnrichmentContext context = scope.readState(EnrichmentState.Context.class);
        boolean approved = context.currentMetadata().entry(WellKnownFields.TAXONOMY_PATH)
                .map(AssetMetadata::isAuthoritative).orElse(false);
        boolean needed = context.asset().type().isTaxonomyClassifiable() && !approved;
        if (!needed) {
            trace(scope).recordSkipped(TAXONOMY_AGENT, StepKind.AI_AGENT, approved
                    ? "Taxonomy path already approved" : "Asset type is not taxonomy-classifiable");
        }
        return needed;
    }

    private static boolean hasMetadataToReview(AgenticScope scope) {
        AssetEnrichmentContext context = scope.readState(EnrichmentState.Context.class);
        CandidateSet candidates = scope.readState(EnrichmentState.Candidates.class);
        boolean review = !candidates.candidates().isEmpty() || !context.currentMetadata().entries().isEmpty();
        if (!review) {
            trace(scope).recordSkipped(QUALITY_AGENT, StepKind.AI_AGENT, "No metadata to review");
        }
        return review;
    }

    private static ApprovalPlan plan(AgenticScope scope) {
        return scope.readState(EnrichmentState.Plan.class);
    }

    private static WorkflowTrace trace(AgenticScope scope) {
        return scope.readState(AgentObservabilityListener.TRACE_KEY) instanceof WorkflowTrace trace
                ? trace : new WorkflowTrace();
    }

    private static WorkflowStageDefinition stage(String id, String name, StepKind kind, String description,
                                                 String parallelGroup, boolean conditional) {
        return new WorkflowStageDefinition(id, name, kind, description, parallelGroup, conditional);
    }

    @Override
    public void destroy() {
        virtualThreads.close();
    }
}

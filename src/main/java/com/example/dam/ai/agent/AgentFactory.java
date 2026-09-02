package com.example.dam.ai.agent;

import com.example.dam.ai.config.AiProperties;
import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.ai.tool.AssetTools;
import com.example.dam.ai.tool.MetadataKnowledgeTools;
import com.example.dam.ai.tool.MetadataTools;
import com.example.dam.ai.tool.ProductTools;
import com.example.dam.ai.tool.TaxonomyTools;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds the narrow LangChain4j agents. Each agent gets its own prompt and only the read-only tools it needs.
 * <ul>
 *   <li>Workflow agents are LangChain4j Agentic agents writing their result to the shared scope.</li>
 *   <li>Standalone use (quality report, search interpretation) builds plain AI Services from the same interfaces
 *       and prompts, as recommended by LangChain4j for calls outside an agentic system.</li>
 * </ul>
 * Tool failures, invalid tool arguments and hallucinated tool names are reported back to the model instead of
 * failing the call.
 */
@Component
@ConditionalOnAiEnabled
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private final ChatModel chatModel;
    private final PromptCatalog prompts;
    private final AiProperties properties;
    private final MeterRegistry meterRegistry;
    private final AssetTools assetTools;
    private final MetadataTools metadataTools;
    private final TaxonomyTools taxonomyTools;
    private final ProductTools productTools;
    private final MetadataKnowledgeTools knowledgeTools;

    public AgentFactory(ChatModel chatModel, PromptCatalog prompts, AiProperties properties,
                        MeterRegistry meterRegistry, AssetTools assetTools, MetadataTools metadataTools,
                        TaxonomyTools taxonomyTools, ProductTools productTools,
                        MetadataKnowledgeTools knowledgeTools) {
        this.chatModel = chatModel;
        this.prompts = prompts;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.assetTools = assetTools;
        this.metadataTools = metadataTools;
        this.taxonomyTools = taxonomyTools;
        this.productTools = productTools;
        this.knowledgeTools = knowledgeTools;
    }

    public MetadataExtractionAgent extractionAgent(Class<? extends TypedKey<?>> outputKey) {
        return agent(MetadataExtractionAgent.class, "metadata-extraction", outputKey, productTools);
    }

    public MetadataEnrichmentAgent enrichmentAgent(Class<? extends TypedKey<?>> outputKey) {
        return agent(MetadataEnrichmentAgent.class, "metadata-enrichment", outputKey, knowledgeTools, assetTools);
    }

    public TaxonomyAgent taxonomyAgent(Class<? extends TypedKey<?>> outputKey) {
        return agent(TaxonomyAgent.class, "taxonomy-classification", outputKey, taxonomyTools);
    }

    public MetadataQualityAgent qualityAgent(Class<? extends TypedKey<?>> outputKey) {
        return agent(MetadataQualityAgent.class, "metadata-quality", outputKey, knowledgeTools, metadataTools);
    }

    public MetadataQualityAgent standaloneQualityAgent() {
        return aiService(MetadataQualityAgent.class, "metadataQualityAgent", "metadata-quality", knowledgeTools,
                metadataTools);
    }

    public SearchIntentAgent standaloneSearchIntentAgent() {
        return aiService(SearchIntentAgent.class, "searchIntentAgent", "search-intent");
    }

    private <T> T agent(Class<T> agentType, String prompt, Class<? extends TypedKey<?>> outputKey, Object... tools) {
        AgentBuilder<T, ?> builder = AgenticServices.agentBuilder(agentType);
        builder.chatModel(chatModel);
        builder.systemMessage(prompts.system(prompt));
        builder.userMessage(prompts.user(prompt));
        builder.outputKey(outputKey);
        if (tools.length > 0) {
            builder.tools(tools);
            builder.maxSequentialToolsInvocations(properties.workflow().maxToolInvocations());
            builder.toolExecutionErrorHandler(toolExecutionErrorHandler(agentType.getSimpleName()));
            builder.toolArgumentsErrorHandler(toolArgumentsErrorHandler());
            builder.hallucinatedToolNameStrategy(AgentFactory::unknownTool);
        }
        return builder.build();
    }

    private <T> T aiService(Class<T> serviceType, String name, String prompt, Object... tools) {
        AiServices<T> builder = AiServices.builder(serviceType)
                .chatModel(chatModel)
                .systemMessageProvider(memoryId -> prompts.system(prompt))
                .userMessageProvider(memoryId -> prompts.user(prompt));
        if (tools.length > 0) {
            builder.tools(tools)
                    .maxSequentialToolsInvocations(properties.workflow().maxToolInvocations())
                    .toolExecutionErrorHandler(toolExecutionErrorHandler(name))
                    .toolArgumentsErrorHandler(toolArgumentsErrorHandler())
                    .hallucinatedToolNameStrategy(AgentFactory::unknownTool)
                    .afterToolExecution(execution -> meterRegistry.counter("dam.ai.tool.calls", "agent", name,
                            "tool", execution.request().name(), "outcome",
                            execution.hasFailed() ? "failure" : "success").increment());
        }
        return builder.build();
    }

    private ToolExecutionErrorHandler toolExecutionErrorHandler(String agent) {
        return (error, context) -> {
            meterRegistry.counter("dam.ai.tool.errors", "agent", agent).increment();
            log.warn("Tool {} failed for {}: {}", context.toolExecutionRequest().name(), agent, error.getMessage());
            return ToolErrorHandlerResult.text(
                    "The tool failed. Continue without this information and lower your confidence accordingly.");
        };
    }

    private static ToolArgumentsErrorHandler toolArgumentsErrorHandler() {
        return (error, context) -> ToolErrorHandlerResult.text(
                "Invalid arguments for tool '%s'. Check the parameter names and types and try again."
                        .formatted(context.toolExecutionRequest().name()));
    }

    private static ToolExecutionResultMessage unknownTool(ToolExecutionRequest request) {
        return ToolExecutionResultMessage.from(request,
                "Unknown tool '%s'. Use only the tools you were given.".formatted(request.name()));
    }
}

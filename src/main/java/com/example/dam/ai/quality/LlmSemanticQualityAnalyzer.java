package com.example.dam.ai.quality;

import com.example.dam.ai.agent.AgentFactory;
import com.example.dam.ai.agent.MetadataQualityAgent;
import com.example.dam.ai.agent.PromptContextRenderer;
import com.example.dam.ai.agent.QualityReviewOutput;
import com.example.dam.ai.config.AiModelInfo;
import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.ai.observability.AiCallObserver;
import com.example.dam.ai.rag.MetadataKnowledgeService;
import com.example.dam.metadata.application.quality.SemanticQualityAnalyzer;
import java.util.List;
import org.springframework.stereotype.Component;

/** Implements the semantic quality port with the {@link MetadataQualityAgent} prompt as a standalone AI service. */
@Component
@ConditionalOnAiEnabled
class LlmSemanticQualityAnalyzer implements SemanticQualityAnalyzer {

    private final MetadataQualityAgent agent;
    private final AiCallObserver observer;
    private final PromptContextRenderer renderer;
    private final MetadataKnowledgeService knowledgeService;
    private final AiModelInfo modelInfo;

    LlmSemanticQualityAnalyzer(AgentFactory agents, AiCallObserver observer, PromptContextRenderer renderer,
                               MetadataKnowledgeService knowledgeService, AiModelInfo modelInfo) {
        this.agent = agents.standaloneQualityAgent();
        this.observer = observer;
        this.renderer = renderer;
        this.knowledgeService = knowledgeService;
        this.modelInfo = modelInfo;
    }

    @Override
    public SemanticQualityAssessment analyze(SemanticQualityRequest request) {
        QualityReviewOutput output = observer.observe("metadataQualityAgent", () -> agent.review(
                renderer.metadataToReview(request.schema(), request.metadata(), List.of()),
                renderer.findings(request.deterministicFindings()),
                knowledgeService.guidanceFor(request.asset().type())));
        return QualityReviewMapper.toAssessment(output, modelInfo.chatModelLabel());
    }
}

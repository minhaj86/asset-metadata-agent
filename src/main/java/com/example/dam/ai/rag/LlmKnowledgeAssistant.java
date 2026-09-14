package com.example.dam.ai.rag;

import com.example.dam.ai.agent.KnowledgeAnswerOutput;
import com.example.dam.ai.agent.MetadataKnowledgeAssistant;
import com.example.dam.ai.agent.PromptCatalog;
import com.example.dam.ai.config.AiModelInfo;
import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.common.domain.CapabilityUnavailableException;
import com.example.dam.knowledge.application.port.KnowledgeAssistant;
import com.example.dam.knowledge.application.port.KnowledgeBase.KnowledgeSnippet;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Governance Q&amp;A using the full LangChain4j RAG pipeline: query transformation, embedding-store content
 * retrieval, content injection and a structured answer. Answers "UNKNOWN" when the knowledge is insufficient.
 */
@Component
@ConditionalOnAiEnabled
class LlmKnowledgeAssistant implements KnowledgeAssistant {

    private static final String UNKNOWN = "UNKNOWN - the metadata governance documentation does not cover this question.";

    private final MetadataKnowledgeAssistant assistant;
    private final AiModelInfo modelInfo;

    LlmKnowledgeAssistant(ChatModel chatModel, MetadataKnowledgeService knowledgeService, PromptCatalog prompts,
                          AiModelInfo modelInfo) {
        this.modelInfo = modelInfo;
        DefaultRetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(query -> List.of(Query.from(query.text().replaceAll("</?question>", "").strip(),
                        query.metadata())))
                .contentRetriever(knowledgeService.contentRetriever())
                .contentInjector(DefaultContentInjector.builder()
                        .promptTemplate(PromptTemplate.from("{{userMessage}}\n\n<knowledge>\n{{contents}}\n</knowledge>"))
                        .metadataKeysToInclude(List.of(MarkdownSectionSplitter.TITLE, MarkdownSectionSplitter.SECTION))
                        .build())
                .build();
        this.assistant = AiServices.builder(MetadataKnowledgeAssistant.class)
                .chatModel(chatModel)
                .systemMessageProvider(memoryId -> prompts.system("knowledge-assistant"))
                .userMessageProvider(memoryId -> prompts.user("knowledge-assistant"))
                .retrievalAugmentor(augmentor)
                .build();
    }

    @Override
    public KnowledgeAnswer ask(String question) {
        Result<KnowledgeAnswerOutput> result;
        try {
            result = assistant.answer(question);
        } catch (CapabilityUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CapabilityUnavailableException("AI_UNAVAILABLE", "Knowledge assistant unavailable: "
                    + e.getClass().getSimpleName(), e);
        }
        List<KnowledgeSnippet> sources = result.sources() == null ? List.of()
                : result.sources().stream().map(LlmKnowledgeAssistant::snippet).toList();
        KnowledgeAnswerOutput output = result.content();
        boolean grounded = output != null && output.grounded() && !sources.isEmpty();
        return new KnowledgeAnswer(grounded ? output.answer() : UNKNOWN, sources, grounded, modelInfo.chatModelLabel());
    }

    private static KnowledgeSnippet snippet(Content content) {
        TextSegment segment = content.textSegment();
        Object score = content.metadata().get(ContentMetadata.SCORE);
        String text = segment.text();
        int newline = text.indexOf('\n');
        return new KnowledgeSnippet(segment.metadata().getString(KnowledgeIngestionService.DOCUMENT_ID),
                segment.metadata().getString(MarkdownSectionSplitter.TITLE),
                segment.metadata().getString(MarkdownSectionSplitter.SECTION),
                newline < 0 ? text : text.substring(newline + 1),
                score instanceof Number number ? Math.round(number.doubleValue() * 1000) / 1000.0 : 0.0);
    }
}

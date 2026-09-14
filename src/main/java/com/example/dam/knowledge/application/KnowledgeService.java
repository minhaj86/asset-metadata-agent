package com.example.dam.knowledge.application;

import com.example.dam.common.domain.CapabilityUnavailableException;
import com.example.dam.knowledge.application.port.KnowledgeAssistant;
import com.example.dam.knowledge.application.port.KnowledgeAssistant.KnowledgeAnswer;
import com.example.dam.knowledge.application.port.KnowledgeBase;
import com.example.dam.knowledge.application.port.KnowledgeBase.KnowledgeDocumentInfo;
import com.example.dam.knowledge.application.port.KnowledgeBase.KnowledgeSnippet;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

    private final ObjectProvider<KnowledgeBase> knowledgeBase;
    private final ObjectProvider<KnowledgeAssistant> assistant;

    public KnowledgeService(ObjectProvider<KnowledgeBase> knowledgeBase, ObjectProvider<KnowledgeAssistant> assistant) {
        this.knowledgeBase = knowledgeBase;
        this.assistant = assistant;
    }

    public List<KnowledgeSnippet> search(String query, int maxResults) {
        return requireKnowledgeBase().search(query, maxResults);
    }

    public List<KnowledgeDocumentInfo> documents() {
        return requireKnowledgeBase().documents();
    }

    public KnowledgeAnswer ask(String question) {
        KnowledgeAssistant knowledgeAssistant = assistant.getIfAvailable();
        if (knowledgeAssistant == null) {
            throw new CapabilityUnavailableException("AI_DISABLED", "The knowledge assistant is disabled");
        }
        return knowledgeAssistant.ask(question);
    }

    private KnowledgeBase requireKnowledgeBase() {
        KnowledgeBase base = knowledgeBase.getIfAvailable();
        if (base == null) {
            throw new CapabilityUnavailableException("AI_DISABLED", "The knowledge base is disabled");
        }
        return base;
    }
}

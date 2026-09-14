package com.example.dam.knowledge.application.port;

import com.example.dam.knowledge.application.port.KnowledgeBase.KnowledgeSnippet;
import java.util.List;

/** Port for answering governance questions grounded in the knowledge base (RAG with an LLM). */
public interface KnowledgeAssistant {

    KnowledgeAnswer ask(String question);

    /**
     * @param grounded whether relevant knowledge was found; ungrounded questions are answered with "unknown"
     */
    record KnowledgeAnswer(String answer, List<KnowledgeSnippet> sources, boolean grounded, String model) {
        public KnowledgeAnswer {
            sources = List.copyOf(sources);
        }
    }
}

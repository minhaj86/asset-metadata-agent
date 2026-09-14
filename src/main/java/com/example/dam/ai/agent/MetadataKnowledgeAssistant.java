package com.example.dam.ai.agent;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.V;

/** Classic LangChain4j AI service with a retrieval augmentor (RAG) answering governance questions. */
public interface MetadataKnowledgeAssistant {

    Result<KnowledgeAnswerOutput> answer(@V("question") String question);
}

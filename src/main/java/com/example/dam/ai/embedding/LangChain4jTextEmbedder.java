package com.example.dam.ai.embedding;

import com.example.dam.ai.config.AiModelInfo;
import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.search.application.port.TextEmbedder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/** Adapts a LangChain4j {@link EmbeddingModel} to the search module's embedding port. */
@Component
@ConditionalOnAiEnabled
class LangChain4jTextEmbedder implements TextEmbedder {

    private final EmbeddingModel embeddingModel;
    private final AiModelInfo modelInfo;

    LangChain4jTextEmbedder(EmbeddingModel embeddingModel, AiModelInfo modelInfo) {
        this.embeddingModel = embeddingModel;
        this.modelInfo = modelInfo;
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text).content().vector();
    }

    @Override
    public String modelName() {
        return modelInfo.embeddingModel();
    }

    @Override
    public int dimension() {
        return embeddingModel.dimension();
    }
}

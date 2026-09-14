package com.example.dam.search.application.port;

/** Port for turning text into an embedding vector (implemented with LangChain4j in the AI layer). */
public interface TextEmbedder {

    float[] embed(String text);

    String modelName();

    int dimension();
}

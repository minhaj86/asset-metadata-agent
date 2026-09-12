package com.example.dam.ai.model.demo;

/** Computes the demo model's answer for one response schema. */
interface DemoResponder {

    /** Simple name of the structured-output type, e.g. {@code ExtractionOutput}. */
    String schemaName();

    DemoReply reply(DemoConversation conversation);
}

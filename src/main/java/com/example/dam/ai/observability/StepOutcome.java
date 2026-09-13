package com.example.dam.ai.observability;

/** Implemented by workflow step results that can describe their outcome in one line for the execution trace. */
public interface StepOutcome {

    String outcomeSummary();
}

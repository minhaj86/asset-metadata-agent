package com.example.dam.common.observability;

import java.util.Optional;
import org.slf4j.MDC;

/** MDC keys shared by HTTP requests, workflows and asynchronous work. */
public final class CorrelationContext {

    public static final String CORRELATION_ID = "correlationId";
    public static final String WORKFLOW_RUN_ID = "workflowRunId";
    public static final String ASSET_ID = "assetId";

    private CorrelationContext() {
    }

    public static Optional<String> correlationId() {
        return Optional.ofNullable(MDC.get(CORRELATION_ID));
    }
}

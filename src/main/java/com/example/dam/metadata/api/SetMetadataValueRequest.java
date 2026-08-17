package com.example.dam.metadata.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetMetadataValueRequest(
        @NotNull @Schema(description = "String, number, boolean, ISO date string or list for repeatable fields",
                example = "BLUE") Object value,
        @Size(max = 1000) @Schema(description = "Why the value is set (stored in the audit trail)") String reason) {
}

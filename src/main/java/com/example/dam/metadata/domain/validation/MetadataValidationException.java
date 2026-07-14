package com.example.dam.metadata.domain.validation;

import com.example.dam.common.domain.BusinessRuleViolationException;
import java.util.List;
import java.util.stream.Collectors;

/** Raised when a write would store metadata that violates the schema. */
public class MetadataValidationException extends BusinessRuleViolationException {

    private final transient List<ValidationIssue> issues;

    public MetadataValidationException(List<ValidationIssue> issues) {
        super("METADATA_VALIDATION_FAILED", "Metadata validation failed: " + issues.stream()
                .filter(ValidationIssue::isError)
                .map(ValidationIssue::message)
                .collect(Collectors.joining("; ")));
        this.issues = List.copyOf(issues);
    }

    public List<ValidationIssue> issues() {
        return issues;
    }
}

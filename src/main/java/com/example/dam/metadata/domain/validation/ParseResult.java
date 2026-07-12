package com.example.dam.metadata.domain.validation;

import com.example.dam.metadata.domain.value.MetadataValue;
import java.util.List;

public record ParseResult(MetadataValue value, List<ValidationIssue> issues) {

    public ParseResult {
        issues = List.copyOf(issues);
    }

    static ParseResult success(MetadataValue value) {
        return new ParseResult(value, List.of());
    }

    static ParseResult failure(List<ValidationIssue> issues) {
        return new ParseResult(null, issues);
    }

    static ParseResult failure(ValidationIssue issue) {
        return failure(List.of(issue));
    }

    public boolean isSuccess() {
        return value != null && issues.isEmpty();
    }

    public MetadataValue valueOrThrow() {
        if (!isSuccess()) {
            throw new MetadataValidationException(issues);
        }
        return value;
    }
}

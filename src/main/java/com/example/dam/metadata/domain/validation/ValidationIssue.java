package com.example.dam.metadata.domain.validation;

import java.util.Objects;

public record ValidationIssue(IssueSeverity severity, IssueCode code, String field, String message) {

    public ValidationIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public static ValidationIssue error(IssueCode code, String field, String message) {
        return new ValidationIssue(IssueSeverity.ERROR, code, field, message);
    }

    public static ValidationIssue warning(IssueCode code, String field, String message) {
        return new ValidationIssue(IssueSeverity.WARNING, code, field, message);
    }

    public boolean isError() {
        return severity == IssueSeverity.ERROR;
    }
}

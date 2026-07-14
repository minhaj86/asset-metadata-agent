package com.example.dam.metadata.domain.validation;

import java.util.List;

public record ValidationReport(List<ValidationIssue> issues) {

    public ValidationReport {
        issues = List.copyOf(issues);
    }

    public List<ValidationIssue> errors() {
        return issues.stream().filter(ValidationIssue::isError).toList();
    }

    public List<ValidationIssue> warnings() {
        return issues.stream().filter(issue -> !issue.isError()).toList();
    }

    public boolean isValid() {
        return issues.stream().noneMatch(ValidationIssue::isError);
    }
}

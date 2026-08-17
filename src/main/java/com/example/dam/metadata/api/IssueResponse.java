package com.example.dam.metadata.api;

import com.example.dam.metadata.domain.validation.IssueCode;
import com.example.dam.metadata.domain.validation.IssueSeverity;
import com.example.dam.metadata.domain.validation.ValidationIssue;

public record IssueResponse(IssueSeverity severity, IssueCode code, String field, String message) {

    static IssueResponse from(ValidationIssue issue) {
        return new IssueResponse(issue.severity(), issue.code(), issue.field(), issue.message());
    }
}

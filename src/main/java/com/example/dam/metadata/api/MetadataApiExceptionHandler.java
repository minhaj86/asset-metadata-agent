package com.example.dam.metadata.api;

import com.example.dam.common.api.GlobalExceptionHandler;
import com.example.dam.metadata.domain.validation.MetadataValidationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Adds the individual validation findings to 422 responses. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class MetadataApiExceptionHandler {

    @ExceptionHandler(MetadataValidationException.class)
    ProblemDetail handleValidation(MetadataValidationException e) {
        ProblemDetail problem = GlobalExceptionHandler.problem(HttpStatus.UNPROCESSABLE_CONTENT, e.code(), e.getMessage());
        problem.setProperty("issues", e.issues().stream().map(IssueResponse::from).toList());
        return problem;
    }
}

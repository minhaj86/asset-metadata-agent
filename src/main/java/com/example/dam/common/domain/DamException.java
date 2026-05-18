package com.example.dam.common.domain;

/**
 * Base class for expected, business-level failures. Each failure carries a stable machine-readable code
 * and a category that the API layer maps to an HTTP status.
 */
public abstract class DamException extends RuntimeException {

    public enum Category { NOT_FOUND, CONFLICT, INVALID_REQUEST, BUSINESS_RULE, NOT_PERMITTED, UNAVAILABLE }

    private final String code;

    protected DamException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected DamException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public abstract Category category();
}

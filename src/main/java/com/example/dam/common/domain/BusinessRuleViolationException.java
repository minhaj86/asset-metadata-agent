package com.example.dam.common.domain;

/** A request was well-formed but violates a business rule (HTTP 422). */
public class BusinessRuleViolationException extends DamException {

    public BusinessRuleViolationException(String code, String message) {
        super(code, message);
    }

    @Override
    public Category category() {
        return Category.BUSINESS_RULE;
    }
}

package com.example.dam.common.domain;

/** An optional capability (typically AI) is temporarily unavailable; core DAM functionality is unaffected. */
public class CapabilityUnavailableException extends DamException {

    public CapabilityUnavailableException(String code, String message) {
        super(code, message);
    }

    public CapabilityUnavailableException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    @Override
    public Category category() {
        return Category.UNAVAILABLE;
    }
}

package com.example.dam.common.domain;

public class InvalidRequestException extends DamException {

    public InvalidRequestException(String code, String message) {
        super(code, message);
    }

    @Override
    public Category category() {
        return Category.INVALID_REQUEST;
    }
}

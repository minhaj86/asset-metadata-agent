package com.example.dam.common.domain;

public class ConflictException extends DamException {

    public ConflictException(String code, String message) {
        super(code, message);
    }

    @Override
    public Category category() {
        return Category.CONFLICT;
    }
}

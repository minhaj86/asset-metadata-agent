package com.example.dam.common.domain;

public class ResourceNotFoundException extends DamException {

    public ResourceNotFoundException(String code, String message) {
        super(code, message);
    }

    @Override
    public Category category() {
        return Category.NOT_FOUND;
    }
}

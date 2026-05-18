package com.example.dam.common.domain;

/** The caller is identified but not allowed to perform the action (HTTP 403). */
public class ActionNotPermittedException extends DamException {

    public ActionNotPermittedException(String code, String message) {
        super(code, message);
    }

    @Override
    public Category category() {
        return Category.NOT_PERMITTED;
    }
}

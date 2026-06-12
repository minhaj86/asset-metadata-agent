package com.example.dam.metadata.domain.value;

import java.util.List;

public record BooleanValue(boolean value) implements MetadataValue {

    @Override
    public List<String> tokens() {
        return List.of(Boolean.toString(value));
    }

    @Override
    public Object toPlainValue() {
        return value;
    }
}

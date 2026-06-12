package com.example.dam.metadata.domain.value;

import java.util.List;
import java.util.Objects;

public record TextValue(String value) implements MetadataValue {

    public TextValue {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public List<String> tokens() {
        return List.of(value);
    }

    @Override
    public Object toPlainValue() {
        return value;
    }
}

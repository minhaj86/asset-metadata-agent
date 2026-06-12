package com.example.dam.metadata.domain.value;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DateValue(LocalDate value) implements MetadataValue {

    public DateValue {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public List<String> tokens() {
        return List.of(value.toString());
    }

    @Override
    public Object toPlainValue() {
        return value.toString();
    }
}

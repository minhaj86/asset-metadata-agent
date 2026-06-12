package com.example.dam.metadata.domain.value;

import java.util.List;
import java.util.Objects;

/** Value of a repeatable field. Items are scalars; nesting is not allowed. */
public record ListValue(List<MetadataValue> items) implements MetadataValue {

    public ListValue {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (items.stream().anyMatch(ListValue.class::isInstance)) {
            throw new IllegalArgumentException("Nested list values are not supported");
        }
    }

    public static ListValue ofTexts(List<String> texts) {
        return new ListValue(texts.stream().<MetadataValue>map(TextValue::new).toList());
    }

    @Override
    public List<String> tokens() {
        return items.stream().flatMap(item -> item.tokens().stream()).toList();
    }

    @Override
    public Object toPlainValue() {
        return items.stream().map(MetadataValue::toPlainValue).toList();
    }
}

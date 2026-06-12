package com.example.dam.metadata.domain.value;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record NumberValue(BigDecimal value) implements MetadataValue {

    public NumberValue {
        Objects.requireNonNull(value, "value");
    }

    public static NumberValue of(long value) {
        return new NumberValue(BigDecimal.valueOf(value));
    }

    public boolean isIntegral() {
        return value.stripTrailingZeros().scale() <= 0;
    }

    @Override
    public List<String> tokens() {
        return List.of(value.stripTrailingZeros().toPlainString());
    }

    @Override
    public Object toPlainValue() {
        return value;
    }
}

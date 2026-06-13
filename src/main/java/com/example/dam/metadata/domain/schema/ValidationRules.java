package com.example.dam.metadata.domain.schema;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Declarative per-field constraints. All attributes are optional. */
public record ValidationRules(
        String pattern,
        Integer minLength,
        Integer maxLength,
        BigDecimal minValue,
        BigDecimal maxValue,
        Integer maxItems) {

    public static final ValidationRules NONE = new ValidationRules(null, null, null, null, null, null);

    public ValidationRules {
        if (pattern != null) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid validation pattern: " + pattern, e);
            }
        }
        if (minLength != null && maxLength != null && minLength > maxLength) {
            throw new IllegalArgumentException("minLength must not exceed maxLength");
        }
        if (minValue != null && maxValue != null && minValue.compareTo(maxValue) > 0) {
            throw new IllegalArgumentException("minValue must not exceed maxValue");
        }
        if (maxItems != null && maxItems < 1) {
            throw new IllegalArgumentException("maxItems must be positive");
        }
    }

    public Optional<Pattern> compiledPattern() {
        return Optional.ofNullable(pattern).map(Pattern::compile);
    }
}

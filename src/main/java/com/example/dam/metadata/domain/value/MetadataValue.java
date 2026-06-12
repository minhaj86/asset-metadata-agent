package com.example.dam.metadata.domain.value;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Typed metadata value. Scalars are text, number, boolean or date; repeatable fields hold a {@link ListValue}
 * of scalars. Values are immutable and carry no provenance (see {@code AssetMetadata}).
 */
public sealed interface MetadataValue permits TextValue, NumberValue, BooleanValue, DateValue, ListValue {

    /** Scalar string forms: one entry for scalars, one per item for lists. */
    List<String> tokens();

    /** JSON-compatible representation: String, BigDecimal, Boolean, ISO-8601 date String or a List of those. */
    Object toPlainValue();

    default String display() {
        return String.join(", ", tokens());
    }

    /** Case- and order-insensitive comparison used to detect "no change" and duplicates. */
    default boolean isEquivalentTo(MetadataValue other) {
        if (other == null) {
            return false;
        }
        if (this instanceof ListValue || other instanceof ListValue) {
            return normalizedTokens(this).equals(normalizedTokens(other));
        }
        return getClass() == other.getClass() && normalize(tokens().getFirst()).equals(normalize(other.tokens().getFirst()));
    }

    static String normalize(String token) {
        return token.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizedTokens(MetadataValue value) {
        return value.tokens().stream().map(MetadataValue::normalize).collect(Collectors.toSet());
    }
}

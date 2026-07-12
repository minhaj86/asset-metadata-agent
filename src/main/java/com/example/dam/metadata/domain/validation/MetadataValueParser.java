package com.example.dam.metadata.domain.validation;

import static com.example.dam.metadata.domain.validation.ValidationIssue.error;

import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.value.BooleanValue;
import com.example.dam.metadata.domain.value.DateValue;
import com.example.dam.metadata.domain.value.ListValue;
import com.example.dam.metadata.domain.value.MetadataValue;
import com.example.dam.metadata.domain.value.NumberValue;
import com.example.dam.metadata.domain.value.TextValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Converts raw JSON-compatible input (API payloads, AI output, stored JSON) into typed {@link MetadataValue}s
 * according to the field definition. Controlled-vocabulary values are canonicalised (e.g. "light blue" ->
 * LIGHT_BLUE); constraint checks are left to {@link MetadataValueValidator}.
 */
public final class MetadataValueParser {

    public ParseResult parse(MetadataFieldDefinition field, Object raw) {
        if (raw == null) {
            return ParseResult.failure(error(IssueCode.EMPTY_VALUE, field.key(), "Value must not be null"));
        }
        if (raw instanceof MetadataValue value) {
            return ParseResult.success(value);
        }
        if (raw instanceof Collection<?> collection) {
            if (field.repeatable()) {
                return parseList(field, collection);
            }
            if (collection.size() == 1) {
                return parseScalar(field, collection.iterator().next());
            }
            return ParseResult.failure(error(IssueCode.NOT_REPEATABLE, field.key(),
                    "Field '%s' accepts a single value but %d were given".formatted(field.key(), collection.size())));
        }
        ParseResult scalar = parseScalar(field, raw);
        if (field.repeatable() && scalar.isSuccess()) {
            return ParseResult.success(new ListValue(List.of(scalar.value())));
        }
        return scalar;
    }

    /** Best-effort typing without a definition, e.g. for stored values of fields removed from the schema. */
    public MetadataValue parseUntyped(Object raw) {
        if (raw instanceof MetadataValue value) {
            return value;
        }
        if (raw instanceof Collection<?> collection) {
            return new ListValue(collection.stream()
                    .map(item -> item instanceof Collection<?> ? new TextValue(String.valueOf(item)) : parseUntyped(item))
                    .toList());
        }
        if (raw instanceof Boolean bool) {
            return new BooleanValue(bool);
        }
        if (raw instanceof Number number) {
            return new NumberValue(new BigDecimal(number.toString()));
        }
        return new TextValue(String.valueOf(raw));
    }

    private ParseResult parseList(MetadataFieldDefinition field, Collection<?> rawItems) {
        List<MetadataValue> items = new ArrayList<>();
        List<ValidationIssue> issues = new ArrayList<>();
        for (Object rawItem : rawItems) {
            if (rawItem == null || rawItem instanceof String text && text.isBlank()) {
                continue;
            }
            if (rawItem instanceof Collection<?>) {
                issues.add(error(IssueCode.INVALID_DATA_TYPE, field.key(), "Nested lists are not supported"));
                continue;
            }
            ParseResult item = parseScalar(field, rawItem);
            if (item.isSuccess()) {
                items.add(item.value());
            } else {
                issues.addAll(item.issues());
            }
        }
        if (!issues.isEmpty()) {
            return ParseResult.failure(issues);
        }
        if (items.isEmpty()) {
            return ParseResult.failure(error(IssueCode.EMPTY_VALUE, field.key(), "At least one value is required"));
        }
        return ParseResult.success(new ListValue(items));
    }

    private ParseResult parseScalar(MetadataFieldDefinition field, Object raw) {
        return switch (field.dataType()) {
            case STRING, TEXT, URL, TAXONOMY_PATH -> parseText(field, raw);
            case INTEGER -> parseNumber(field, raw, true);
            case DECIMAL -> parseNumber(field, raw, false);
            case BOOLEAN -> parseBoolean(field, raw);
            case DATE -> parseDate(field, raw);
        };
    }

    private ParseResult parseText(MetadataFieldDefinition field, Object raw) {
        if (!(raw instanceof CharSequence || raw instanceof Number || raw instanceof Boolean)) {
            return typeMismatch(field, raw);
        }
        String text = raw.toString().strip();
        if (text.isEmpty()) {
            return ParseResult.failure(error(IssueCode.EMPTY_VALUE, field.key(), "Value must not be blank"));
        }
        if (field.hasControlledVocabulary()) {
            text = field.canonicalAllowedValue(text).orElse(text);
        }
        return ParseResult.success(new TextValue(text));
    }

    private ParseResult parseNumber(MetadataFieldDefinition field, Object raw, boolean integral) {
        BigDecimal number;
        try {
            if (raw instanceof Number n) {
                number = new BigDecimal(n.toString());
            } else if (raw instanceof CharSequence text) {
                number = new BigDecimal(text.toString().strip());
            } else {
                return typeMismatch(field, raw);
            }
        } catch (NumberFormatException e) {
            return typeMismatch(field, raw);
        }
        NumberValue value = new NumberValue(number);
        if (integral && !value.isIntegral()) {
            return typeMismatch(field, raw);
        }
        return ParseResult.success(value);
    }

    private ParseResult parseBoolean(MetadataFieldDefinition field, Object raw) {
        if (raw instanceof Boolean bool) {
            return ParseResult.success(new BooleanValue(bool));
        }
        if (raw instanceof CharSequence text) {
            return switch (text.toString().strip().toLowerCase(Locale.ROOT)) {
                case "true", "yes", "y", "1" -> ParseResult.success(new BooleanValue(true));
                case "false", "no", "n", "0" -> ParseResult.success(new BooleanValue(false));
                default -> typeMismatch(field, raw);
            };
        }
        return typeMismatch(field, raw);
    }

    private ParseResult parseDate(MetadataFieldDefinition field, Object raw) {
        if (raw instanceof LocalDate date) {
            return ParseResult.success(new DateValue(date));
        }
        if (raw instanceof CharSequence text) {
            try {
                return ParseResult.success(new DateValue(LocalDate.parse(text.toString().strip())));
            } catch (DateTimeParseException e) {
                return typeMismatch(field, raw);
            }
        }
        return typeMismatch(field, raw);
    }

    private static ParseResult typeMismatch(MetadataFieldDefinition field, Object raw) {
        return ParseResult.failure(error(IssueCode.INVALID_DATA_TYPE, field.key(),
                "Value '%s' is not a valid %s".formatted(raw, field.dataType())));
    }
}

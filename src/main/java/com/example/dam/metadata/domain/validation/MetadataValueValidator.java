package com.example.dam.metadata.domain.validation;

import static com.example.dam.metadata.domain.validation.ValidationIssue.error;
import static com.example.dam.metadata.domain.validation.ValidationIssue.warning;

import com.example.dam.metadata.domain.schema.MetadataDataType;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.ValidationRules;
import com.example.dam.metadata.domain.value.BooleanValue;
import com.example.dam.metadata.domain.value.DateValue;
import com.example.dam.metadata.domain.value.ListValue;
import com.example.dam.metadata.domain.value.MetadataValue;
import com.example.dam.metadata.domain.value.NumberValue;
import com.example.dam.metadata.domain.value.TextValue;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic validation of a single field value against its definition. */
public final class MetadataValueValidator {

    private static final Set<String> URL_SCHEMES = Set.of("http", "https");
    private static final int MAX_LISTED_VOCABULARY = 12;

    private final ReferenceDataLookup referenceData;

    public MetadataValueValidator(ReferenceDataLookup referenceData) {
        this.referenceData = Objects.requireNonNull(referenceData, "referenceData");
    }

    public List<ValidationIssue> validate(MetadataFieldDefinition field, MetadataValue value) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (value instanceof ListValue list) {
            validateList(field, list, issues);
        } else {
            validateScalar(field, value, issues);
        }
        return issues;
    }

    private void validateList(MetadataFieldDefinition field, ListValue list, List<ValidationIssue> issues) {
        if (!field.repeatable()) {
            issues.add(error(IssueCode.NOT_REPEATABLE, field.key(),
                    "Field '%s' accepts a single value".formatted(field.key())));
        }
        if (list.items().isEmpty()) {
            issues.add(error(IssueCode.EMPTY_VALUE, field.key(), "At least one value is required"));
            return;
        }
        Integer maxItems = field.validationRules().maxItems();
        if (maxItems != null && list.items().size() > maxItems) {
            issues.add(error(IssueCode.TOO_MANY_VALUES, field.key(),
                    "Field '%s' allows at most %d values but has %d".formatted(field.key(), maxItems, list.items().size())));
        }
        List<String> duplicates = duplicates(list);
        if (!duplicates.isEmpty()) {
            issues.add(warning(IssueCode.DUPLICATE_VALUES, field.key(), "Duplicate values: " + duplicates));
        }
        list.items().forEach(item -> validateScalar(field, item, issues));
    }

    private void validateScalar(MetadataFieldDefinition field, MetadataValue value, List<ValidationIssue> issues) {
        if (!isCompatible(field.dataType(), value)) {
            issues.add(error(IssueCode.INVALID_DATA_TYPE, field.key(), "Field '%s' expects %s but holds '%s'"
                    .formatted(field.key(), field.dataType(), value.display())));
            return;
        }
        if (value instanceof TextValue text) {
            validateText(field, text.value(), issues);
        } else if (value instanceof NumberValue number) {
            validateNumber(field, number, issues);
        }
    }

    private void validateText(MetadataFieldDefinition field, String text, List<ValidationIssue> issues) {
        if (text.isBlank()) {
            issues.add(error(IssueCode.EMPTY_VALUE, field.key(), "Value must not be blank"));
            return;
        }
        if (field.hasControlledVocabulary() && !field.allowedValues().contains(text)) {
            List<String> allowed = field.allowedValues();
            String listed = allowed.size() > MAX_LISTED_VOCABULARY
                    ? allowed.subList(0, MAX_LISTED_VOCABULARY) + "..."
                    : allowed.toString();
            issues.add(error(IssueCode.VALUE_NOT_ALLOWED, field.key(),
                    "Value '%s' is not in the controlled vocabulary of '%s': %s".formatted(text, field.key(), listed)));
        }
        ValidationRules rules = field.validationRules();
        if (rules.minLength() != null && text.length() < rules.minLength()
                || rules.maxLength() != null && text.length() > rules.maxLength()) {
            issues.add(error(IssueCode.LENGTH_OUT_OF_RANGE, field.key(), "Length %d outside [%s..%s]"
                    .formatted(text.length(), rules.minLength(), rules.maxLength())));
        }
        rules.compiledPattern()
                .filter(pattern -> !pattern.matcher(text).matches())
                .ifPresent(pattern -> issues.add(error(IssueCode.PATTERN_MISMATCH, field.key(),
                        "Value '%s' does not match pattern %s".formatted(text, pattern.pattern()))));
        if (field.dataType() == MetadataDataType.URL && !isWellFormedUrl(text)) {
            issues.add(error(IssueCode.MALFORMED_URL, field.key(), "Malformed URL: " + text));
        }
        if (field.dataType() == MetadataDataType.TAXONOMY_PATH && !referenceData.taxonomyPathExists(text)) {
            issues.add(error(IssueCode.UNKNOWN_TAXONOMY_PATH, field.key(), "Unknown taxonomy path: " + text));
        }
    }

    private static void validateNumber(MetadataFieldDefinition field, NumberValue number, List<ValidationIssue> issues) {
        ValidationRules rules = field.validationRules();
        if (rules.minValue() != null && number.value().compareTo(rules.minValue()) < 0
                || rules.maxValue() != null && number.value().compareTo(rules.maxValue()) > 0) {
            issues.add(error(IssueCode.VALUE_OUT_OF_RANGE, field.key(), "Value %s outside [%s..%s]"
                    .formatted(number.display(), rules.minValue(), rules.maxValue())));
        }
    }

    private static boolean isCompatible(MetadataDataType type, MetadataValue value) {
        return switch (type) {
            case STRING, TEXT, URL, TAXONOMY_PATH -> value instanceof TextValue;
            case INTEGER -> value instanceof NumberValue number && number.isIntegral();
            case DECIMAL -> value instanceof NumberValue;
            case BOOLEAN -> value instanceof BooleanValue;
            case DATE -> value instanceof DateValue;
        };
    }

    private static boolean isWellFormedUrl(String text) {
        try {
            URI uri = new URI(text);
            return uri.isAbsolute() && uri.getScheme() != null && URL_SCHEMES.contains(uri.getScheme().toLowerCase())
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static List<String> duplicates(ListValue list) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        list.tokens().forEach(token -> counts.merge(MetadataValue.normalize(token), 1, Integer::sum));
        return counts.entrySet().stream().filter(entry -> entry.getValue() > 1).map(Map.Entry::getKey).toList();
    }
}

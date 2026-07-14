package com.example.dam.metadata.domain.validation;

import static com.example.dam.metadata.domain.validation.ValidationIssue.error;
import static com.example.dam.metadata.domain.validation.ValidationIssue.warning;

import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.schema.CrossFieldRule;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.value.DateValue;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic validation of an asset's complete metadata against the effective schema. Everything Java can
 * decide (presence, types, vocabularies, rights, date ranges, URLs, taxonomy references) is decided here; the LLM
 * is only consulted for soft, semantic quality aspects.
 */
public final class MetadataValidator {

    private final MetadataValueValidator valueValidator;

    public MetadataValidator(MetadataValueValidator valueValidator) {
        this.valueValidator = Objects.requireNonNull(valueValidator, "valueValidator");
    }

    public ValidationReport validate(MetadataSchema schema, AssetMetadataSnapshot metadata, LocalDate today) {
        List<ValidationIssue> issues = new ArrayList<>();

        metadata.entries().forEach((key, entry) -> {
            Optional<MetadataFieldDefinition> field = schema.field(key);
            if (field.isEmpty()) {
                issues.add(error(IssueCode.UNSUPPORTED_FIELD, key,
                        "Field '%s' is not defined in metadata schema v%d".formatted(key, schema.version())));
            } else {
                issues.addAll(valueValidator.validate(field.get(), entry.value()));
            }
        });

        schema.requiredFields().stream()
                .filter(key -> !metadata.has(key))
                .forEach(key -> issues.add(error(IssueCode.MISSING_REQUIRED_FIELD, key,
                        "Required field '%s' is missing for %s".formatted(key, schema.assetType()))));
        schema.recommendedFields().stream()
                .filter(key -> !metadata.has(key))
                .forEach(key -> issues.add(warning(IssueCode.MISSING_RECOMMENDED_FIELD, key,
                        "Recommended field '%s' is missing".formatted(key))));

        schema.rules().forEach(rule -> issues.addAll(evaluate(rule, metadata, today)));
        return new ValidationReport(issues);
    }

    private static List<ValidationIssue> evaluate(CrossFieldRule rule, AssetMetadataSnapshot metadata, LocalDate today) {
        return switch (rule) {
            case CrossFieldRule.DateRange range -> {
                Optional<LocalDate> start = date(metadata, range.startField());
                Optional<LocalDate> end = date(metadata, range.endField());
                if (start.isPresent() && end.isPresent() && start.get().isAfter(end.get())) {
                    yield List.of(error(IssueCode.INVALID_DATE_RANGE, range.endField(),
                            "'%s' (%s) is before '%s' (%s)".formatted(range.endField(), end.get(),
                                    range.startField(), start.get())));
                }
                yield List.of();
            }
            case CrossFieldRule.RightsExpiry expiry -> date(metadata, expiry.field())
                    .map(end -> {
                        if (end.isBefore(today)) {
                            return List.of(error(IssueCode.RIGHTS_EXPIRED, expiry.field(),
                                    "Usage rights expired on " + end));
                        }
                        if (!end.isAfter(today.plusDays(expiry.warningWindowDays()))) {
                            return List.of(warning(IssueCode.RIGHTS_EXPIRING_SOON, expiry.field(),
                                    "Usage rights expire on %s (within %d days)".formatted(end,
                                            expiry.warningWindowDays())));
                        }
                        return List.<ValidationIssue>of();
                    })
                    .orElse(List.of());
            case CrossFieldRule.ConditionalRequired conditional -> {
                boolean conditionMet = metadata.value(conditional.whenField())
                        .map(value -> value.tokens().stream()
                                .anyMatch(token -> token.equalsIgnoreCase(conditional.whenValue())))
                        .orElse(false);
                if (conditionMet && !metadata.has(conditional.requiredField())) {
                    yield List.of(error(IssueCode.CONDITIONAL_FIELD_MISSING, conditional.requiredField(),
                            "'%s' is required when '%s' is %s".formatted(conditional.requiredField(),
                                    conditional.whenField(), conditional.whenValue())));
                }
                yield List.of();
            }
        };
    }

    private static Optional<LocalDate> date(AssetMetadataSnapshot metadata, String field) {
        return metadata.value(field)
                .filter(DateValue.class::isInstance)
                .map(DateValue.class::cast)
                .map(DateValue::value);
    }
}

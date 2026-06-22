package com.example.dam.metadata.domain.schema;

import java.util.Objects;
import java.util.Set;

/** Deterministic rules spanning several fields, configured in the schema. */
public sealed interface CrossFieldRule {

    String id();

    /** Fields the rule references; used to validate the schema itself. */
    Set<String> referencedFields();

    /** {@code startField} must not be after {@code endField}. */
    record DateRange(String id, String startField, String endField) implements CrossFieldRule {
        public DateRange {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(startField, "startField");
            Objects.requireNonNull(endField, "endField");
        }

        @Override
        public Set<String> referencedFields() {
            return Set.of(startField, endField);
        }
    }

    /** Usage rights in {@code field} must not be expired; warns within {@code warningWindowDays} of expiry. */
    record RightsExpiry(String id, String field, int warningWindowDays) implements CrossFieldRule {
        public RightsExpiry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(field, "field");
            if (warningWindowDays < 0) {
                throw new IllegalArgumentException("warningWindowDays must not be negative");
            }
        }

        @Override
        public Set<String> referencedFields() {
            return Set.of(field);
        }
    }

    /** When {@code whenField} equals {@code whenValue} (case-insensitive), {@code requiredField} must be present. */
    record ConditionalRequired(String id, String whenField, String whenValue, String requiredField)
            implements CrossFieldRule {
        public ConditionalRequired {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(whenField, "whenField");
            Objects.requireNonNull(whenValue, "whenValue");
            Objects.requireNonNull(requiredField, "requiredField");
        }

        @Override
        public Set<String> referencedFields() {
            return Set.of(whenField, requiredField);
        }
    }
}

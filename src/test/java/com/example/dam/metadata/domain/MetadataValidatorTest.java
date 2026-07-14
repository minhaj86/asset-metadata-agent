package com.example.dam.metadata.domain;

import static com.example.dam.support.MetadataFixtures.completeProductImage;
import static com.example.dam.support.MetadataFixtures.schema;
import static com.example.dam.support.MetadataFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.domain.validation.IssueCode;
import com.example.dam.metadata.domain.validation.IssueSeverity;
import com.example.dam.metadata.domain.validation.MetadataValidator;
import com.example.dam.metadata.domain.validation.MetadataValueValidator;
import com.example.dam.metadata.domain.validation.ValidationIssue;
import com.example.dam.metadata.domain.validation.ValidationReport;
import com.example.dam.metadata.domain.value.TextValue;
import com.example.dam.support.MetadataFixtures;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MetadataValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private final MetadataValidator validator =
            new MetadataValidator(new MetadataValueValidator(MetadataFixtures.REFERENCE_DATA));

    @Test
    void completeProductImageIsValid() {
        ValidationReport report = validate(completeProductImage());

        assertThat(report.isValid()).isTrue();
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void missingMandatoryMetadataIsDetected() {
        ValidationReport report = validate(Map.of("productId", "12345"));

        assertThat(report.isValid()).isFalse();
        assertThat(codesFor(report, IssueCode.MISSING_REQUIRED_FIELD))
                .containsExactlyInAnyOrder("productType", "color", "angleFacing", "shotType", "copyrightOwner",
                        "usageRightsEnd", "altText");
        assertThat(report.warnings()).extracting(ValidationIssue::code).contains(IssueCode.MISSING_RECOMMENDED_FIELD);
    }

    @Test
    void invalidDataTypeIsDetected() {
        ValidationReport report = validate(with("usageRightsEnd", new TextValue("next summer")));

        assertThat(codesFor(report, IssueCode.INVALID_DATA_TYPE)).containsExactly("usageRightsEnd");
    }

    @Test
    void valueOutsideControlledVocabularyIsDetected() {
        ValidationReport report = validate(with("color", new TextValue("TURQUOISE")));

        assertThat(codesFor(report, IssueCode.VALUE_NOT_ALLOWED)).containsExactly("color");
    }

    @Test
    void expiredRightsAreErrorsAndSoonExpiringRightsAreWarnings() {
        ValidationReport expired = validate(with("usageRightsEnd", "2026-09-30"));
        ValidationReport expiringSoon = validate(with("usageRightsEnd", "2026-10-20"));

        assertThat(expired.errors()).extracting(ValidationIssue::code).contains(IssueCode.RIGHTS_EXPIRED);
        assertThat(expiringSoon.isValid()).isTrue();
        assertThat(expiringSoon.warnings()).extracting(ValidationIssue::code).contains(IssueCode.RIGHTS_EXPIRING_SOON);
    }

    @Test
    void invalidDateRangeIsDetected() {
        Map<String, Object> values = with("usageRightsStart", "2028-01-01");

        assertThat(codesFor(validate(values), IssueCode.INVALID_DATE_RANGE)).containsExactly("usageRightsEnd");
    }

    @Test
    void unsupportedMetadataKeyIsDetected() {
        ValidationReport report = validate(with("favouriteColour", "teal"));

        assertThat(codesFor(report, IssueCode.UNSUPPORTED_FIELD)).containsExactly("favouriteColour");
    }

    @Test
    void duplicateValuesAreWarnings() {
        ValidationReport report = validate(with("keywords", List.of("shirt", "Shirt", "oxford")));

        assertThat(report.isValid()).isTrue();
        assertThat(report.warnings()).extracting(ValidationIssue::code).containsExactly(IssueCode.DUPLICATE_VALUES);
    }

    @Test
    void malformedUrlIsDetected() {
        ValidationReport report = validate(with("productPageUrl", "htp:/broken url"));

        assertThat(codesFor(report, IssueCode.MALFORMED_URL)).containsExactly("productPageUrl");
    }

    @Test
    void unknownTaxonomyPathIsDetected() {
        ValidationReport report = validate(with("taxonomyPath", "Fashion > Men > Clothing > Space Suits"));

        assertThat(codesFor(report, IssueCode.UNKNOWN_TAXONOMY_PATH)).containsExactly("taxonomyPath");
    }

    @Test
    void conditionalRequirementIsEnforced() {
        ValidationReport report = validate(with("intendedForABTest", true));

        assertThat(codesFor(report, IssueCode.CONDITIONAL_FIELD_MISSING)).containsExactly("experimentIdentifier");
    }

    @Test
    void patternViolationIsDetected() {
        ValidationReport report = validate(with("productId", "ABC"));

        assertThat(codesFor(report, IssueCode.PATTERN_MISMATCH)).containsExactly("productId");
        assertThat(report.errors()).allMatch(issue -> issue.severity() == IssueSeverity.ERROR);
    }

    private ValidationReport validate(Map<String, Object> values) {
        return validator.validate(schema(AssetType.PRODUCT_IMAGE), snapshot(UUID.randomUUID(), values), TODAY);
    }

    private static Map<String, Object> with(String key, Object value) {
        Map<String, Object> values = new HashMap<>(completeProductImage());
        values.put(key, value);
        return values;
    }

    private static List<String> codesFor(ValidationReport report, IssueCode code) {
        return report.issues().stream().filter(issue -> issue.code() == code).map(ValidationIssue::field).toList();
    }
}

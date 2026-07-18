package com.example.dam.metadata.domain;

import static com.example.dam.support.MetadataFixtures.completeProductImage;
import static com.example.dam.support.MetadataFixtures.schema;
import static com.example.dam.support.MetadataFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.domain.quality.CompletenessCalculator;
import com.example.dam.metadata.domain.quality.CompletenessScore;
import com.example.dam.metadata.domain.quality.QualityScoreCalculator;
import com.example.dam.metadata.domain.validation.IssueCode;
import com.example.dam.metadata.domain.validation.ValidationIssue;
import com.example.dam.metadata.domain.validation.ValidationReport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MetadataCompletenessTest {

    private final CompletenessCalculator calculator = new CompletenessCalculator();
    private final QualityScoreCalculator scoreCalculator = new QualityScoreCalculator();

    @Test
    void completenessIsCalculatedFromSchemaRequirements() {
        CompletenessScore score = calculator.calculate(schema(AssetType.PRODUCT_IMAGE),
                snapshot(UUID.randomUUID(), Map.of("productId", "12345", "color", "BLUE", "title", "Shirt")));

        assertThat(score.requiredTotal()).isEqualTo(8);
        assertThat(score.requiredPresent()).isEqualTo(2);
        assertThat(score.recommendedTotal()).isEqualTo(6);
        assertThat(score.recommendedPresent()).isEqualTo(1);
        assertThat(score.requiredPercentage()).isEqualTo(25.0);
        assertThat(score.overallPercentage()).isEqualTo(21.4);
        assertThat(score.missingRequired()).contains("angleFacing", "copyrightOwner").doesNotContain("productId");
    }

    @Test
    void completeMetadataScoresOneHundred() {
        CompletenessScore score = calculator.calculate(schema(AssetType.PRODUCT_IMAGE),
                snapshot(UUID.randomUUID(), completeProductImage()));

        assertThat(score.overallPercentage()).isEqualTo(100.0);
        assertThat(scoreCalculator.score(score, new ValidationReport(List.of()))).isEqualTo(100);
    }

    @Test
    void qualityScorePenalisesFindingsButNotMissingFieldsTwice() {
        CompletenessScore score = calculator.calculate(schema(AssetType.PRODUCT_IMAGE),
                snapshot(UUID.randomUUID(), completeProductImage()));
        ValidationReport report = new ValidationReport(List.of(
                ValidationIssue.error(IssueCode.RIGHTS_EXPIRED, "usageRightsEnd", "expired"),
                ValidationIssue.warning(IssueCode.DUPLICATE_VALUES, "keywords", "duplicates"),
                ValidationIssue.error(IssueCode.MISSING_REQUIRED_FIELD, "altText", "missing")));

        assertThat(scoreCalculator.score(score, report)).isEqualTo(100 - 15 - 3);
    }
}

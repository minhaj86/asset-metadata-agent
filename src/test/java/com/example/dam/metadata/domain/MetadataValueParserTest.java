package com.example.dam.metadata.domain;

import static com.example.dam.support.MetadataFixtures.field;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.metadata.domain.validation.IssueCode;
import com.example.dam.metadata.domain.validation.MetadataValueParser;
import com.example.dam.metadata.domain.validation.ParseResult;
import com.example.dam.metadata.domain.validation.ValidationIssue;
import com.example.dam.metadata.domain.value.BooleanValue;
import com.example.dam.metadata.domain.value.DateValue;
import com.example.dam.metadata.domain.value.ListValue;
import com.example.dam.metadata.domain.value.NumberValue;
import com.example.dam.metadata.domain.value.TextValue;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetadataValueParserTest {

    private final MetadataValueParser parser = new MetadataValueParser();

    @Test
    void canonicalisesControlledVocabularyValues() {
        assertThat(parser.parse(field("color"), "light blue").value()).isEqualTo(new TextValue("LIGHT_BLUE"));
        assertThat(parser.parse(field("angleFacing"), "front").value()).isEqualTo(new TextValue("FRONT"));
    }

    @Test
    void keepsUnknownVocabularyValueSoValidationCanReportIt() {
        assertThat(parser.parse(field("color"), "turquoise").value()).isEqualTo(new TextValue("turquoise"));
    }

    @Test
    void parsesTypedScalars() {
        assertThat(parser.parse(field("modelPresent"), "yes").value()).isEqualTo(new BooleanValue(true));
        assertThat(parser.parse(field("modelCount"), 2).value()).isEqualTo(NumberValue.of(2));
        assertThat(parser.parse(field("usageRightsEnd"), "2027-12-31").value())
                .isEqualTo(new DateValue(LocalDate.of(2027, 12, 31)));
    }

    @Test
    void wrapsScalarForRepeatableFieldAndSkipsBlankItems() {
        assertThat(parser.parse(field("keywords"), "shirt").value()).isEqualTo(ListValue.ofTexts(List.of("shirt")));
        assertThat(parser.parse(field("keywords"), List.of("shirt", " ", "oxford")).value())
                .isEqualTo(ListValue.ofTexts(List.of("shirt", "oxford")));
    }

    @Test
    void rejectsInvalidInput() {
        assertThat(codes(parser.parse(field("color"), List.of("BLUE", "RED")))).containsExactly(IssueCode.NOT_REPEATABLE);
        assertThat(codes(parser.parse(field("modelCount"), "1.5"))).containsExactly(IssueCode.INVALID_DATA_TYPE);
        assertThat(codes(parser.parse(field("usageRightsEnd"), "31/12/2027"))).containsExactly(IssueCode.INVALID_DATA_TYPE);
        assertThat(codes(parser.parse(field("title"), "  "))).containsExactly(IssueCode.EMPTY_VALUE);
    }

    private static List<IssueCode> codes(ParseResult result) {
        assertThat(result.isSuccess()).isFalse();
        return result.issues().stream().map(ValidationIssue::code).toList();
    }
}

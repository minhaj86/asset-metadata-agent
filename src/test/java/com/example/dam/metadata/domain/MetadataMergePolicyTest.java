package com.example.dam.metadata.domain;

import static com.example.dam.support.MetadataFixtures.aiValidated;
import static com.example.dam.support.MetadataFixtures.field;
import static com.example.dam.support.MetadataFixtures.value;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.metadata.domain.proposal.MetadataMergePolicy;
import com.example.dam.metadata.domain.proposal.MetadataMergePolicy.MergeKind;
import com.example.dam.metadata.domain.proposal.MetadataMergePolicy.MergeResult;
import com.example.dam.metadata.domain.value.ListValue;
import com.example.dam.metadata.domain.value.TextValue;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MetadataMergePolicyTest {

    private final MetadataMergePolicy mergePolicy = new MetadataMergePolicy();

    @Test
    void valueForEmptyFieldIsNew() {
        MergeResult result = mergePolicy.merge(field("color"), Optional.empty(), value("color", "BLUE"));

        assertThat(result.kind()).isEqualTo(MergeKind.NEW_VALUE);
        assertThat(result.resultingValue()).isEqualTo(new TextValue("BLUE"));
    }

    @Test
    void singleValuedFieldIsReplacedOrUnchanged() {
        assertThat(mergePolicy.merge(field("color"), Optional.of(aiValidated("color", "RED")), value("color", "BLUE")).kind())
                .isEqualTo(MergeKind.REPLACEMENT);
        assertThat(mergePolicy.merge(field("title"), Optional.of(aiValidated("title", "Oxford Shirt")),
                value("title", "oxford shirt ")).kind()).isEqualTo(MergeKind.UNCHANGED);
    }

    @Test
    void repeatableFieldsMergeAdditivelyKeepingOrderAndRemovingCaseInsensitiveDuplicates() {
        MergeResult result = mergePolicy.merge(field("keywords"),
                Optional.of(aiValidated("keywords", List.of("shirt", "blue"))),
                value("keywords", List.of("Blue", "oxford", "cotton")));

        assertThat(result.kind()).isEqualTo(MergeKind.ADDITIVE);
        assertThat(result.resultingValue()).isEqualTo(ListValue.ofTexts(List.of("shirt", "blue", "oxford", "cotton")));
    }

    @Test
    void subsetOfExistingListIsUnchanged() {
        MergeResult result = mergePolicy.merge(field("keywords"),
                Optional.of(aiValidated("keywords", List.of("shirt", "blue"))), value("keywords", List.of("SHIRT")));

        assertThat(result.kind()).isEqualTo(MergeKind.UNCHANGED);
    }
}

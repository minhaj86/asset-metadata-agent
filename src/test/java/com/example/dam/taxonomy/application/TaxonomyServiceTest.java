package com.example.dam.taxonomy.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.support.InMemoryFakes;
import com.example.dam.taxonomy.domain.TaxonomyNode;
import org.junit.jupiter.api.Test;

class TaxonomyServiceTest {

    private final TaxonomyService taxonomyService = new TaxonomyService(InMemoryFakes::taxonomy);

    @Test
    void searchRanksMostSpecificMatchingLeafFirst() {
        assertThat(taxonomyService.search("shirt men", 3))
                .extracting(TaxonomyNode::path)
                .first()
                .isEqualTo("Fashion > Men > Clothing > Shirts");
        assertThat(taxonomyService.search("red dresses for women", 1))
                .extracting(TaxonomyNode::id)
                .containsExactly("fashion.women.clothing.dresses");
    }

    @Test
    void searchWithoutMeaningfulTermsReturnsNothing() {
        assertThat(taxonomyService.search("the of and", 5)).isEmpty();
    }

    @Test
    void pathLookupIsExact() {
        assertThat(taxonomyService.pathExists("Fashion > Men > Clothing > Shirts")).isTrue();
        assertThat(taxonomyService.pathExists("Fashion > Men > Clothing > Space Suits")).isFalse();
    }

    @Test
    void termsAreSingularisedAndStopWordsRemoved() {
        assertThat(TaxonomyService.terms("Men's Dresses and T-shirts")).containsExactlyInAnyOrder("men", "dress", "t-shirt");
    }
}

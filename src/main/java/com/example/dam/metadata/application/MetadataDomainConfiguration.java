package com.example.dam.metadata.application;

import com.example.dam.metadata.domain.proposal.ApprovalPolicy;
import com.example.dam.metadata.domain.proposal.ConfidenceThresholds;
import com.example.dam.metadata.domain.proposal.MetadataMergePolicy;
import com.example.dam.metadata.domain.proposal.ProposalReviewPolicy;
import com.example.dam.metadata.domain.quality.CompletenessCalculator;
import com.example.dam.metadata.domain.quality.QualityScoreCalculator;
import com.example.dam.metadata.domain.validation.MetadataValidator;
import com.example.dam.metadata.domain.validation.MetadataValueParser;
import com.example.dam.metadata.domain.validation.MetadataValueValidator;
import com.example.dam.metadata.domain.validation.ReferenceDataLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exposes the framework-free domain services as Spring beans. */
@Configuration(proxyBeanMethods = false)
class MetadataDomainConfiguration {

    @Bean
    MetadataValueParser metadataValueParser() {
        return new MetadataValueParser();
    }

    @Bean
    MetadataValueValidator metadataValueValidator(ReferenceDataLookup referenceDataLookup) {
        return new MetadataValueValidator(referenceDataLookup);
    }

    @Bean
    MetadataValidator metadataValidator(MetadataValueValidator valueValidator) {
        return new MetadataValidator(valueValidator);
    }

    @Bean
    CompletenessCalculator completenessCalculator() {
        return new CompletenessCalculator();
    }

    @Bean
    QualityScoreCalculator qualityScoreCalculator() {
        return new QualityScoreCalculator();
    }

    @Bean
    MetadataMergePolicy metadataMergePolicy() {
        return new MetadataMergePolicy();
    }

    @Bean
    ApprovalPolicy approvalPolicy(ConfidenceThresholdProperties thresholds, MetadataMergePolicy mergePolicy,
                                  MetadataValueValidator valueValidator) {
        return new ApprovalPolicy(new ConfidenceThresholds(thresholds.autoApprove(), thresholds.humanReview()),
                mergePolicy, valueValidator);
    }

    @Bean
    ProposalReviewPolicy proposalReviewPolicy(ConfidenceThresholdProperties thresholds) {
        return new ProposalReviewPolicy(new ConfidenceThresholds(thresholds.autoApprove(), thresholds.humanReview()));
    }
}

package com.example.dam.metadata.application.quality;

import com.example.dam.metadata.application.MetadataSchemaService;
import com.example.dam.metadata.application.MetadataValidationService;
import com.example.dam.metadata.application.MetadataValidationService.AssetValidation;
import com.example.dam.metadata.application.quality.MetadataQualityReport.AiStatus;
import com.example.dam.metadata.application.quality.SemanticQualityAnalyzer.SemanticQualityAssessment;
import com.example.dam.metadata.application.quality.SemanticQualityAnalyzer.SemanticQualityRequest;
import com.example.dam.metadata.domain.quality.QualityScoreCalculator;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class MetadataQualityService {

    private static final Logger log = LoggerFactory.getLogger(MetadataQualityService.class);

    private final MetadataValidationService validationService;
    private final MetadataSchemaService schemaService;
    private final QualityScoreCalculator scoreCalculator;
    private final ObjectProvider<SemanticQualityAnalyzer> semanticAnalyzer;
    private final Clock clock;

    public MetadataQualityService(MetadataValidationService validationService, MetadataSchemaService schemaService,
                                  QualityScoreCalculator scoreCalculator,
                                  ObjectProvider<SemanticQualityAnalyzer> semanticAnalyzer, Clock clock) {
        this.validationService = validationService;
        this.schemaService = schemaService;
        this.scoreCalculator = scoreCalculator;
        this.semanticAnalyzer = semanticAnalyzer;
        this.clock = clock;
    }

    public MetadataQualityReport assess(UUID assetId, boolean includeAi) {
        AssetValidation validation = validationService.validate(assetId);
        int score = scoreCalculator.score(validation.completeness(), validation.report());

        AiStatus aiStatus = AiStatus.SKIPPED;
        SemanticQualityAssessment assessment = null;
        SemanticQualityAnalyzer analyzer = includeAi ? semanticAnalyzer.getIfAvailable() : null;
        if (includeAi && analyzer == null) {
            aiStatus = AiStatus.UNAVAILABLE;
        } else if (analyzer != null) {
            try {
                assessment = analyzer.analyze(new SemanticQualityRequest(validation.asset(),
                        schemaService.schemaFor(validation.asset().type()), validation.metadata(),
                        validation.report().issues()));
                aiStatus = AiStatus.COMPLETED;
            } catch (RuntimeException e) {
                log.warn("Semantic quality analysis unavailable for asset {}: {}", assetId, e.getMessage());
                aiStatus = AiStatus.UNAVAILABLE;
            }
        }
        return new MetadataQualityReport(
                assetId,
                validation.asset().type(),
                validation.schemaVersion(),
                score,
                validation.completeness().overallPercentage(),
                validation.completeness().requiredPercentage(),
                validation.completeness().missingRequired(),
                validation.report().errors(),
                validation.report().warnings(),
                aiStatus,
                assessment == null ? List.of() : assessment.suggestions(),
                assessment == null ? null : assessment.confidence(),
                assessment == null ? null : assessment.summary(),
                assessment == null ? null : assessment.model(),
                clock.instant());
    }
}

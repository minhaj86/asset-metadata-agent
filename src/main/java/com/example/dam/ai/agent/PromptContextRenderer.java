package com.example.dam.ai.agent;

import com.example.dam.ai.config.ConditionalOnAiEnabled;
import com.example.dam.metadata.application.enrichment.AssetEnrichmentContext;
import com.example.dam.metadata.application.enrichment.MetadataCandidate;
import com.example.dam.metadata.application.port.AssetAnalysisService.AssetAnalysis;
import com.example.dam.metadata.application.port.ProductInformationClient.ProductInformation;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.validation.ValidationIssue;
import com.example.dam.search.application.port.SearchIntentInterpreter.SearchVocabulary;
import com.example.dam.taxonomy.domain.TaxonomyNode;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Renders application objects as compact JSON for prompts. Only information an agent needs is included
 * (e.g. the field catalog lists AI-enrichable fields only), which keeps prompts small and limits what a model
 * can be tempted to touch.
 */
@Component
@ConditionalOnAiEnabled
public class PromptContextRenderer {

    private final JsonMapper json;

    public PromptContextRenderer(JsonMapper json) {
        this.json = json;
    }

    public String assetContext(AssetEnrichmentContext context) {
        ObjectNode root = json.createObjectNode();
        ObjectNode asset = root.putObject("asset");
        asset.put("externalAssetId", context.asset().externalAssetId());
        asset.put("filename", context.asset().filename());
        asset.put("mimeType", context.asset().mimeType());
        asset.put("assetType", context.asset().type().name());
        ObjectNode embedded = root.putObject("embeddedMetadata");
        context.embeddedMetadata().forEach(embedded::put);
        context.analysis().ifPresentOrElse(analysis -> analysis(root.putObject("imageAnalysis"), analysis),
                () -> root.putNull("imageAnalysis"));
        context.productInformation().ifPresentOrElse(product -> product(root.putObject("product"), product),
                () -> root.putNull("product"));
        return write(root);
    }

    public String currentMetadata(AssetMetadataSnapshot metadata) {
        ObjectNode root = json.createObjectNode();
        metadata.entries().forEach((key, entry) -> entry(root.putObject(key), entry));
        return write(root);
    }

    /** AI-enrichable fields of the effective schema; the only fields agents may propose. */
    public String fieldCatalog(MetadataSchema schema) {
        ArrayNode fields = json.createArrayNode();
        for (MetadataFieldDefinition field : schema.aiEnrichableFields()) {
            ObjectNode node = fields.addObject();
            node.put("key", field.key());
            node.put("displayName", field.displayName());
            node.put("type", field.dataType().name());
            node.put("repeatable", field.repeatable());
            node.put("required", schema.isRequired(field.key()));
            if (field.hasControlledVocabulary()) {
                ArrayNode allowed = node.putArray("allowedValues");
                field.allowedValues().forEach(allowed::add);
            }
            if (field.validationRules().maxLength() != null) {
                node.put("maxLength", field.validationRules().maxLength());
            }
            if (field.validationRules().pattern() != null) {
                node.put("pattern", field.validationRules().pattern());
            }
            node.put("description", field.description());
        }
        return write(fields);
    }

    public String taxonomyCandidates(List<TaxonomyNode> nodes) {
        ArrayNode array = json.createArrayNode();
        nodes.forEach(node -> {
            ObjectNode item = array.addObject();
            item.put("id", node.id());
            item.put("path", node.path());
            item.put("leaf", node.leaf());
        });
        return write(array);
    }

    /** Current values plus proposed values (marked {@code "status": "proposed"}) for fields not yet set. */
    public String workingMetadata(AssetMetadataSnapshot current, List<MetadataCandidate> proposed) {
        ObjectNode root = json.createObjectNode();
        current.entries().forEach((key, entry) -> entry(root.putObject(key), entry));
        proposed.stream()
                .filter(candidate -> !current.has(candidate.fieldKey()))
                .forEach(candidate -> {
                    ObjectNode node = root.putObject(candidate.fieldKey());
                    node.putPOJO("value", candidate.value().toPlainValue());
                    node.put("status", "proposed");
                    node.put("confidence", candidate.confidence());
                });
        return write(root);
    }

    /** Metadata as it would look after applying proposals, for semantic review. */
    public String metadataToReview(MetadataSchema schema, AssetMetadataSnapshot current, List<MetadataCandidate> proposed) {
        ObjectNode root = json.createObjectNode();
        root.put("assetType", schema.assetType().name());
        ObjectNode fields = root.putObject("fields");
        current.entries().forEach((key, entry) -> {
            ObjectNode node = fields.putObject(key);
            node.putPOJO("value", entry.value().toPlainValue());
            node.put("state", "current");
        });
        proposed.forEach(candidate -> {
            ObjectNode node = fields.putObject(candidate.fieldKey());
            node.putPOJO("value", candidate.value().toPlainValue());
            node.put("state", "proposed");
        });
        return write(root);
    }

    public String findings(List<ValidationIssue> issues) {
        ArrayNode array = json.createArrayNode();
        issues.forEach(issue -> {
            ObjectNode node = array.addObject();
            node.put("severity", issue.severity().name());
            node.put("code", issue.code().name());
            node.put("field", issue.field());
            node.put("message", issue.message());
        });
        return write(array);
    }

    public String searchableFields(SearchVocabulary vocabulary) {
        ArrayNode array = json.createArrayNode();
        vocabulary.fields().forEach(field -> {
            ObjectNode node = array.addObject();
            node.put("key", field.key());
            node.put("displayName", field.displayName());
            node.put("type", field.dataType());
            if (!field.allowedValues().isEmpty()) {
                ArrayNode allowed = node.putArray("allowedValues");
                field.allowedValues().forEach(allowed::add);
            }
        });
        return write(array);
    }

    public String assetTypes(SearchVocabulary vocabulary) {
        return write(json.valueToTree(vocabulary.assetTypes()));
    }

    private void entry(ObjectNode node, AssetMetadata entry) {
        node.putPOJO("value", entry.value().toPlainValue());
        node.put("source", entry.source().name());
        node.put("status", entry.status().name());
        node.put("authoritative", entry.isAuthoritative());
    }

    private static void analysis(ObjectNode node, AssetAnalysis analysis) {
        node.put("analyzer", analysis.analyzer());
        ArrayNode colors = node.putArray("dominantColors");
        analysis.dominantColors().forEach(colors::add);
        node.put("orientation", analysis.orientation());
        node.put("background", analysis.background());
        ArrayNode objects = node.putArray("detectedObjects");
        analysis.detectedObjects().forEach(objects::add);
        node.put("personCount", analysis.personCount());
        node.put("shotType", analysis.shotType());
        ArrayNode ocr = node.putArray("ocrText");
        analysis.ocrText().forEach(ocr::add);
    }

    private static void product(ObjectNode node, ProductInformation product) {
        node.put("productId", product.productId());
        node.put("articleCode", product.articleCode());
        node.put("name", product.name());
        node.put("brand", product.brand());
        node.put("department", product.department());
        node.put("productType", product.productType());
        node.put("color", product.color());
        ArrayNode materials = node.putArray("materials");
        product.materials().forEach(materials::add);
        node.put("season", product.season());
        node.put("description", product.description());
    }

    private String write(Object node) {
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }
}

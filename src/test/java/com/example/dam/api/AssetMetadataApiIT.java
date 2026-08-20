package com.example.dam.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** REST API: registration, metadata CRUD with provenance, deterministic validation, audit and schema. */
class AssetMetadataApiIT extends AbstractIntegrationTest {

    @Test
    void registersAssetWithInitialMetadataAndExposesProvenance() {
        UUID id = registerAsset(uniqueId("API"), AssetType.PRODUCT_IMAGE, "navy-chinos-front.jpg",
                Map.of("productId", "34567", "color", "navy"));

        JsonNode asset = get("/api/assets/" + id);
        assertThat(asset.get("status").asString()).isEqualTo("DRAFT");
        assertThat(asset.get("type").asString()).isEqualTo("PRODUCT_IMAGE");

        JsonNode color = metadataValue(id, "color");
        assertThat(color.get("value").asString()).isEqualTo("NAVY");
        assertThat(color.get("source").asString()).isEqualTo("MANUAL");
        assertThat(color.get("status").asString()).isEqualTo("APPROVED");
        assertThat(color.get("authoritative").asBoolean()).isTrue();
        assertThat(color.get("updatedBy").asString()).isEqualTo(USER);
    }

    @Test
    void missingMandatoryMetadataIsDetected() {
        UUID id = registerAsset(uniqueId("API"), AssetType.PRODUCT_IMAGE, "blue-shirt-back.jpg",
                Map.of("productId", "12345", "color", "BLUE"));

        JsonNode validation = post("/api/assets/" + id + "/validate", null, USER, 200);

        assertThat(validation.get("valid").asBoolean()).isFalse();
        assertThat(validation.get("requiredCompletenessPercentage").asDouble()).isEqualTo(25.0);
        assertThat(fields(validation.get("errors"), "MISSING_REQUIRED_FIELD")).containsExactlyInAnyOrder(
                "productType", "angleFacing", "shotType", "copyrightOwner", "usageRightsEnd", "altText");
    }

    @Test
    void invalidValuesAreRejectedAndUnsupportedFieldsAreBadRequests() {
        UUID id = registerAsset(uniqueId("API"), AssetType.PRODUCT_IMAGE, "shirt.jpg", Map.of("productId", "12345"));

        JsonNode invalid = put("/api/assets/" + id + "/metadata/color", Map.of("value", "TURQUOISE-ISH"), USER, 422);
        assertThat(invalid.get("code").asString()).isEqualTo("METADATA_VALIDATION_FAILED");
        assertThat(invalid.get("issues").get(0).get("code").asString()).isEqualTo("VALUE_NOT_ALLOWED");

        JsonNode unsupported = put("/api/assets/" + id + "/metadata/favouriteColour", Map.of("value", "teal"), USER, 400);
        assertThat(unsupported.get("code").asString()).isEqualTo("UNSUPPORTED_FIELD");

        Map<String, Object> body = Map.of("externalAssetId", uniqueId("API"), "filename", "x.jpg",
                "mimeType", "image/jpeg", "assetUrl", "https://dam.example.com/x.jpg", "type", "PRODUCT_IMAGE",
                "metadata", Map.of("usageRightsEnd", "next summer"));
        assertThat(post("/api/assets", body, USER, 422).get("issues").get(0).get("code").asString())
                .isEqualTo("INVALID_DATA_TYPE");
    }

    @Test
    void manualChangesAreAuditedAndDriveTheAssetStatus() {
        Map<String, Object> metadata = new HashMap<>(completeProductImage());
        metadata.remove("altText");
        UUID id = registerAsset(uniqueId("API"), AssetType.PRODUCT_IMAGE, "blue-shirt-front.jpg", metadata);
        assertThat(get("/api/assets/" + id).get("status").asString()).isEqualTo("DRAFT");

        put("/api/assets/" + id + "/metadata/altText",
                Map.of("value", "Front view of a blue men's shirt on a white background", "reason", "Copy review"),
                USER, 200);
        assertThat(get("/api/assets/" + id).get("status").asString()).isEqualTo("READY");

        delete("/api/assets/" + id + "/metadata/altText?reason=Rewrite", USER, 204);
        assertThat(get("/api/assets/" + id).get("status").asString()).isEqualTo("DRAFT");

        JsonNode history = get("/api/assets/" + id + "/metadata/history");
        assertThat(history.get(0).get("action").asString()).isEqualTo("VALUE_REMOVED");
        assertThat(history.get(1).get("action").asString()).isEqualTo("VALUE_SET_MANUALLY");
        assertThat(history.get(1).get("field").asString()).isEqualTo("altText");
        assertThat(history.get(1).get("actor").asString()).isEqualTo(USER);
        assertThat(history.get(1).get("reason").asString()).isEqualTo("Copy review");
        assertThat(history.get(1).get("correlationId").isNull()).isFalse();
    }

    @Test
    void duplicateRegistrationIsAConflictAndUnknownAssetsAreNotFound() {
        String externalId = uniqueId("API");
        registerAsset(externalId, AssetType.PRODUCT_IMAGE, "a.jpg", Map.of());

        Map<String, Object> body = Map.of("externalAssetId", externalId, "filename", "a.jpg", "mimeType", "image/jpeg",
                "assetUrl", "https://dam.example.com/a.jpg", "type", "PRODUCT_IMAGE");
        assertThat(post("/api/assets", body, USER, 409).get("code").asString()).isEqualTo("ASSET_ALREADY_REGISTERED");
        assertThat(get("/api/assets/" + UUID.randomUUID(), 404).get("code").asString()).isEqualTo("ASSET_NOT_FOUND");
    }

    @Test
    void registrationResolvesBinaryReferenceFromTheExternalDam() {
        JsonNode asset = post("/api/assets", Map.of("externalAssetId", "DA-200003", "type", "PRODUCT_IMAGE"), USER, 201);

        assertThat(asset.get("filename").asString()).isEqualTo("navy-chinos-front.jpg");
        assertThat(asset.get("assetUrl").asString()).endsWith("/mock-dam/assets/DA-200003/navy-chinos-front.jpg");
    }

    @Test
    void schemaEndpointExposesRequirementsAndAiGovernance() {
        JsonNode schema = get("/api/metadata/schema/PRODUCT_IMAGE");

        assertThat(texts(schema.get("requiredFields"))).contains("productId", "angleFacing", "copyrightOwner");
        JsonNode angleFacing = field(schema, "angleFacing");
        assertThat(angleFacing.get("ai").get("humanApprovalRequired").asBoolean()).isTrue();
        assertThat(field(schema, "usageRightsEnd").get("ai").get("enrichable").asBoolean()).isFalse();
    }

    static Map<String, Object> completeProductImage() {
        return Map.ofEntries(
                Map.entry("productId", "12345"), Map.entry("productType", "SHIRT"), Map.entry("color", "BLUE"),
                Map.entry("angleFacing", "FRONT"), Map.entry("shotType", "STUDIO"), Map.entry("copyrightOwner", "Sample Apparel"),
                Map.entry("usageRightsEnd", "2030-12-31"),
                Map.entry("altText", "Front view of a blue men's shirt on a white background"),
                Map.entry("title", "Oxford Shirt - Front View"), Map.entry("keywords", List.of("shirt", "blue")),
                Map.entry("taxonomyPath", "Fashion > Men > Clothing > Shirts"), Map.entry("backgroundType", "WHITE"),
                Map.entry("material", List.of("COTTON")), Map.entry("description", "Studio image of a blue shirt."));
    }

    private static JsonNode field(JsonNode schema, String key) {
        for (JsonNode field : schema.get("fields").values()) {
            if (field.get("key").asString().equals(key)) {
                return field;
            }
        }
        throw new AssertionError("No field " + key);
    }

    private static List<String> fields(JsonNode issues, String code) {
        List<String> fields = new ArrayList<>();
        issues.values().forEach(issue -> {
            if (issue.get("code").asString().equals(code)) {
                fields.add(issue.get("field").asString());
            }
        });
        return fields;
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.values().forEach(value -> values.add(value.asString()));
        return values;
    }
}

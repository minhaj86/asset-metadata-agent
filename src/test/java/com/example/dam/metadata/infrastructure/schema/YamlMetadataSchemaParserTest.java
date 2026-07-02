package com.example.dam.metadata.infrastructure.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.schema.MetadataSchemaDefinition;
import com.example.dam.support.MetadataFixtures;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class YamlMetadataSchemaParserTest {

    @Test
    void shippedSchemaIsConsistentAndEncodesGovernance() {
        MetadataSchemaDefinition definition = MetadataFixtures.definition();

        assertThat(definition.fields()).hasSize(33);
        assertThat(definition.rules()).hasSize(4);
        MetadataFieldDefinition angleFacing = definition.field("angleFacing").orElseThrow();
        assertThat(angleFacing.allowedValues()).containsExactly("FRONT", "BACK", "LEFT", "RIGHT", "DETAIL");
        assertThat(angleFacing.aiPolicy().enrichable()).isTrue();
        assertThat(angleFacing.aiPolicy().humanApprovalRequired()).isTrue();
        assertThat(definition.field("usageRightsEnd").orElseThrow().aiPolicy().enrichable()).isFalse();
        assertThat(definition.field("keywords").orElseThrow().aiPolicy().autoApply()).isTrue();

        MetadataSchema productImage = definition.forAssetType(AssetType.PRODUCT_IMAGE);
        assertThat(productImage.requiredFields()).containsExactlyInAnyOrder("productId", "productType", "color",
                "angleFacing", "shotType", "copyrightOwner", "usageRightsEnd", "altText");
        assertThat(productImage.rightsExpiryRule()).isPresent();
    }

    @Test
    void requirementReferencingUnknownFieldIsRejected() {
        String yaml = """
                version: 1
                fields:
                  title: { type: STRING }
                assetTypes:
                  PRODUCT_IMAGE:
                    required: [title, nonExistingField]
                """;

        assertThatThrownBy(() -> new YamlMetadataSchemaParser().parse(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonExistingField");
    }

    @Test
    void contradictoryAiPolicyIsRejected() {
        String yaml = """
                version: 1
                fields:
                  title: { type: STRING, ai: { enrichable: true, autoApply: true, humanApprovalRequired: true } }
                """;

        assertThatThrownBy(() -> new YamlMetadataSchemaParser().parse(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

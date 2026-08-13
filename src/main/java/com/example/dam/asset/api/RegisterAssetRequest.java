package com.example.dam.asset.api;

import com.example.dam.asset.domain.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(description = "Registers a reference to an asset owned by the external DAM")
public record RegisterAssetRequest(
        @NotBlank @Size(max = 100) String externalAssetId,
        @Schema(description = "Optional; resolved from the DAM when omitted") @Size(max = 512) String filename,
        @Schema(description = "Optional; resolved from the DAM when omitted") @Size(max = 127) String mimeType,
        @Schema(description = "Optional; resolved from the DAM when omitted") @Size(max = 2048) String assetUrl,
        @NotNull AssetType type,
        @Schema(description = "Initial metadata: field key -> value (string, number, boolean or list)",
                example = "{\"productId\": \"12345\"}") Map<String, Object> metadata,
        @Schema(description = "Provenance of the initial metadata: MANUAL (default), DAM, PRODUCT_SYSTEM or EMBEDDED")
        String metadataSource) {
}

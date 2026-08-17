package com.example.dam.metadata.api;

import com.example.dam.asset.domain.AssetType;
import com.example.dam.metadata.application.MetadataSchemaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metadata/schema")
@Tag(name = "Metadata schema", description = "Configurable field definitions and governance")
class MetadataSchemaController {

    private final MetadataSchemaService schemaService;

    MetadataSchemaController(MetadataSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @GetMapping("/{assetType}")
    @Operation(summary = "Effective metadata schema for an asset type, incl. AI governance per field")
    MetadataSchemaResponse schema(@PathVariable AssetType assetType) {
        return MetadataSchemaResponse.from(schemaService.schemaFor(assetType));
    }
}

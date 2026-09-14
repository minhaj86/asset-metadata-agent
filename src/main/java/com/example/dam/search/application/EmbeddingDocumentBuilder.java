package com.example.dam.search.application;

import com.example.dam.asset.domain.Asset;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.value.BooleanValue;
import com.example.dam.metadata.domain.value.ListValue;
import com.example.dam.metadata.domain.value.MetadataValue;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministically renders the text that represents an asset in vector space, using only fields flagged
 * {@code semanticSearch} in the schema, e.g.
 * <pre>
 * Product image.
 * Product type: shirt.
 * Colour: blue.
 * Keywords: shirt, oxford, blue, menswear, cotton.
 * Taxonomy path: Fashion &gt; Men &gt; Clothing &gt; Shirts.
 * </pre>
 */
@Component
public class EmbeddingDocumentBuilder {

    public String build(Asset asset, MetadataSchema schema, AssetMetadataSnapshot metadata) {
        StringBuilder text = new StringBuilder(humanize(asset.type().name())).append('.');
        for (MetadataFieldDefinition field : schema.fieldDefinitions()) {
            if (field.semanticSearch()) {
                metadata.value(field.key()).ifPresent(value -> text.append('\n')
                        .append(field.displayName()).append(": ").append(render(field, value)).append('.'));
            }
        }
        return text.toString();
    }

    private static String render(MetadataFieldDefinition field, MetadataValue value) {
        if (value instanceof BooleanValue bool) {
            return bool.value() ? "yes" : "no";
        }
        if (value instanceof ListValue list) {
            return list.items().stream().map(item -> render(field, item)).collect(Collectors.joining(", "));
        }
        String text = value.display();
        return field.hasControlledVocabulary() ? humanize(text).toLowerCase(Locale.ROOT) : stripTrailingPeriod(text);
    }

    private static String humanize(String constant) {
        String spaced = constant.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String stripTrailingPeriod(String text) {
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }
}

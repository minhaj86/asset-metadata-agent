package com.example.dam.metadata.domain.proposal;

import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.value.ListValue;
import com.example.dam.metadata.domain.value.MetadataValue;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic merge semantics between an existing value and a proposed value.
 * Repeatable fields merge additively (union, case-insensitive de-duplication, existing order kept);
 * single-valued fields are either unchanged or replaced.
 */
public final class MetadataMergePolicy {

    public enum MergeKind { NEW_VALUE, UNCHANGED, ADDITIVE, REPLACEMENT }

    public record MergeResult(MergeKind kind, MetadataValue resultingValue) {
    }

    public MergeResult merge(MetadataFieldDefinition field, Optional<AssetMetadata> existing, MetadataValue proposed) {
        if (existing.isEmpty()) {
            return new MergeResult(MergeKind.NEW_VALUE, proposed);
        }
        MetadataValue current = existing.get().value();
        if (field.repeatable() && current instanceof ListValue currentList && proposed instanceof ListValue proposedList) {
            ListValue union = union(currentList, proposedList);
            return union.isEquivalentTo(currentList)
                    ? new MergeResult(MergeKind.UNCHANGED, currentList)
                    : new MergeResult(MergeKind.ADDITIVE, union);
        }
        return proposed.isEquivalentTo(current)
                ? new MergeResult(MergeKind.UNCHANGED, current)
                : new MergeResult(MergeKind.REPLACEMENT, proposed);
    }

    private static ListValue union(ListValue current, ListValue proposed) {
        List<MetadataValue> items = new ArrayList<>(current.items());
        Set<String> seen = new LinkedHashSet<>();
        current.tokens().forEach(token -> seen.add(MetadataValue.normalize(token)));
        for (MetadataValue item : proposed.items()) {
            if (seen.add(MetadataValue.normalize(item.tokens().getFirst()))) {
                items.add(item);
            }
        }
        return new ListValue(items);
    }
}

package com.example.dam.audit.domain;

import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.audit.AuditAction;
import java.time.Instant;
import java.util.UUID;

/**
 * Filter for the global audit log; {@code null} attributes are ignored.
 *
 * @param assetText case-insensitive substring of the external asset id
 * @param actorType kind of actor that performed the change
 */
public record AuditQuery(
        UUID assetId,
        String assetText,
        String field,
        MetadataSource source,
        String actor,
        ActorType actorType,
        AuditAction action,
        Instant from,
        Instant to,
        int page,
        int size) {

    public enum ActorType { HUMAN, AI, SYSTEM }

    public AuditQuery {
        assetText = blankToNull(assetText);
        field = blankToNull(field);
        actor = blankToNull(actor);
        if (page < 0 || size < 1 || size > 500) {
            throw new IllegalArgumentException("page must be >= 0 and size within 1..500");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

package com.example.dam.audit.domain;

import com.example.dam.metadata.domain.audit.MetadataAuditEvent;
import java.util.List;
import java.util.UUID;

/** Append-only audit log port. There is intentionally no update or delete operation. */
public interface MetadataAuditRepository {

    void append(MetadataAuditEvent event);

    List<MetadataAuditEvent> findByAssetId(UUID assetId, int limit);

    /** History of one asset, optionally restricted to one field, newest first. */
    List<MetadataAuditEvent> findByAssetId(UUID assetId, String field, int limit);

    Page search(AuditQuery query);

    /**
     * @param externalAssetId {@code null} if the asset no longer exists
     */
    record Entry(MetadataAuditEvent event, String externalAssetId) {
    }

    record Page(List<Entry> items, int page, int size, long totalItems) {
        public Page {
            items = List.copyOf(items);
        }
    }
}

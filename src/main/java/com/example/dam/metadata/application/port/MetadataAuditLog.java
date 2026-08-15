package com.example.dam.metadata.application.port;

import com.example.dam.metadata.domain.audit.MetadataAuditEvent;

/**
 * Append-only audit trail of metadata changes and proposal decisions, implemented by the audit module.
 * Events must be appended inside the transaction of the change they describe.
 */
public interface MetadataAuditLog {

    void append(MetadataAuditEvent event);
}

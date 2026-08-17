package com.example.dam.audit.application;

import com.example.dam.asset.domain.AssetNotFoundException;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.audit.domain.AuditQuery;
import com.example.dam.audit.domain.MetadataAuditRepository;
import com.example.dam.metadata.application.port.MetadataAuditLog;
import com.example.dam.metadata.domain.audit.MetadataAuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetadataAuditService implements MetadataAuditLog {

    private final MetadataAuditRepository repository;
    private final AssetRepository assetRepository;

    public MetadataAuditService(MetadataAuditRepository repository, AssetRepository assetRepository) {
        this.repository = repository;
        this.assetRepository = assetRepository;
    }

    /** Audit events are written in the same transaction as the change they describe. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(MetadataAuditEvent event) {
        repository.append(event);
    }

    @Transactional(readOnly = true)
    public List<MetadataAuditEvent> history(UUID assetId, int limit) {
        return history(assetId, null, limit);
    }

    /** History of one asset, newest first, optionally restricted to one field. */
    @Transactional(readOnly = true)
    public List<MetadataAuditEvent> history(UUID assetId, String field, int limit) {
        if (assetRepository.findById(assetId).isEmpty()) {
            throw new AssetNotFoundException(assetId);
        }
        return repository.findByAssetId(assetId, field == null || field.isBlank() ? null : field.strip(), limit);
    }

    /** Global, filterable audit log across assets, newest first. */
    @Transactional(readOnly = true)
    public MetadataAuditRepository.Page search(AuditQuery query) {
        return repository.search(query);
    }
}

package com.example.dam.metadata.application;

import com.example.dam.asset.application.port.DamClient;
import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.MetadataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes authoritative (human-approved or system-of-record) metadata back to the external DAM after commit.
 * Values that originally came from the DAM are not echoed back. Failures are logged; a production setup would use
 * a transactional outbox for guaranteed delivery.
 */
@Component
@ConditionalOnBooleanProperty(name = "dam.integration.write-back", matchIfMissing = true)
class DamWriteBackListener {

    private static final Logger log = LoggerFactory.getLogger(DamWriteBackListener.class);

    private final AssetRepository assetRepository;
    private final AssetMetadataRepository metadataRepository;
    private final DamClient damClient;

    DamWriteBackListener(AssetRepository assetRepository, AssetMetadataRepository metadataRepository,
                         DamClient damClient) {
        this.assetRepository = assetRepository;
        this.metadataRepository = metadataRepository;
        this.damClient = damClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onMetadataChanged(MetadataChangedEvent event) {
        Optional<Asset> asset = assetRepository.findById(event.assetId());
        if (asset.isEmpty()) {
            return;
        }
        Map<String, String> approved = new LinkedHashMap<>();
        for (AssetMetadata entry : metadataRepository.findByAssetId(event.assetId())) {
            if (event.fieldKeys().contains(entry.fieldKey()) && entry.isAuthoritative()
                    && entry.source() != MetadataSource.DAM) {
                approved.put(entry.fieldKey(), entry.value().display());
            }
        }
        if (approved.isEmpty()) {
            return;
        }
        try {
            damClient.updateMetadata(asset.get().externalAssetId(), approved);
        } catch (RuntimeException e) {
            log.warn("DAM write-back for asset {} failed: {}", asset.get().externalAssetId(), e.getMessage());
        }
    }
}

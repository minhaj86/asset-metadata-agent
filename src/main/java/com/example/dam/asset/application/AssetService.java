package com.example.dam.asset.application;

import com.example.dam.asset.application.port.DamClient;
import com.example.dam.asset.application.port.DamClient.DamAssetRecord;
import com.example.dam.asset.application.port.InitialMetadataImporter;
import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetNotFoundException;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.common.domain.Actor;
import com.example.dam.common.domain.ConflictException;
import com.example.dam.common.domain.InvalidRequestException;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final DamClient damClient;
    private final InitialMetadataImporter metadataImporter;
    private final Clock clock;

    public AssetService(AssetRepository assetRepository, DamClient damClient,
                        InitialMetadataImporter metadataImporter, Clock clock) {
        this.assetRepository = assetRepository;
        this.damClient = damClient;
        this.metadataImporter = metadataImporter;
        this.clock = clock;
    }

    /**
     * Registration request. Filename, MIME type and URL may be omitted, in which case they are resolved from the
     * external DAM by {@code externalAssetId}.
     */
    public record RegisterAssetCommand(String externalAssetId, String filename, String mimeType, URI assetUrl,
                                       AssetType type, Map<String, Object> initialMetadata, String metadataSource) {
        public RegisterAssetCommand {
            initialMetadata = initialMetadata == null ? Map.of() : Map.copyOf(initialMetadata);
        }
    }

    @Transactional
    public Asset register(RegisterAssetCommand command, Actor actor) {
        if (assetRepository.findByExternalAssetId(command.externalAssetId()).isPresent()) {
            throw new ConflictException("ASSET_ALREADY_REGISTERED",
                    "Asset with external id %s is already registered".formatted(command.externalAssetId()));
        }
        RegisterAssetCommand resolved = resolveFromDam(command);
        Asset asset = Asset.register(resolved.externalAssetId(), resolved.filename(), resolved.mimeType(),
                resolved.assetUrl(), resolved.type(), clock.instant());
        assetRepository.insert(asset);
        if (!resolved.initialMetadata().isEmpty()) {
            metadataImporter.importInitialMetadata(asset, resolved.initialMetadata(), resolved.metadataSource(), actor);
        }
        return assetRepository.findById(asset.id()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Asset get(UUID id) {
        return assetRepository.findById(id).orElseThrow(() -> new AssetNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Optional<Asset> findByExternalAssetId(String externalAssetId) {
        return assetRepository.findByExternalAssetId(externalAssetId);
    }

    @Transactional(readOnly = true)
    public List<Asset> list(AssetType type, int page, int size) {
        return assetRepository.findPage(type, page * size, size);
    }

    /** A DAM asset and whether it is already registered here. */
    public record DamAssetView(DamAssetRecord record, boolean registered) {
    }

    /** Browses the external DAM, e.g. to discover assets that are not registered yet. */
    @Transactional(readOnly = true)
    public List<DamAssetView> browseDam(String query, int limit) {
        return damClient.searchAssets(query, limit).stream()
                .map(record -> new DamAssetView(record,
                        assetRepository.findByExternalAssetId(record.externalAssetId()).isPresent()))
                .toList();
    }

    private RegisterAssetCommand resolveFromDam(RegisterAssetCommand command) {
        if (command.filename() != null && command.mimeType() != null && command.assetUrl() != null) {
            return command;
        }
        DamAssetRecord record = damClient.getAsset(command.externalAssetId())
                .orElseThrow(() -> new InvalidRequestException("ASSET_NOT_IN_DAM",
                        "filename, mimeType and assetUrl are required because asset %s is unknown to the DAM"
                                .formatted(command.externalAssetId())));
        return new RegisterAssetCommand(command.externalAssetId(),
                command.filename() != null ? command.filename() : record.filename(),
                command.mimeType() != null ? command.mimeType() : record.mimeType(),
                command.assetUrl() != null ? command.assetUrl() : record.url(),
                command.type(), command.initialMetadata(), command.metadataSource());
    }
}

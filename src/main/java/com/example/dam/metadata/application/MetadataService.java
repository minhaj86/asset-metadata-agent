package com.example.dam.metadata.application;

import com.example.dam.asset.application.port.InitialMetadataImporter;
import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetNotFoundException;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.common.domain.Actor;
import com.example.dam.common.domain.InvalidRequestException;
import com.example.dam.common.domain.ResourceNotFoundException;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.AssetMetadataSnapshot;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.MetadataStatus;
import com.example.dam.metadata.domain.audit.AuditAction;
import com.example.dam.metadata.domain.proposal.MetadataProposal;
import com.example.dam.metadata.domain.proposal.MetadataProposalRepository;
import com.example.dam.metadata.domain.schema.MetadataFieldDefinition;
import com.example.dam.metadata.domain.schema.MetadataSchema;
import com.example.dam.metadata.domain.validation.IssueCode;
import com.example.dam.metadata.domain.validation.MetadataValidationException;
import com.example.dam.metadata.domain.validation.MetadataValueParser;
import com.example.dam.metadata.domain.validation.MetadataValueValidator;
import com.example.dam.metadata.domain.validation.ParseResult;
import com.example.dam.metadata.domain.validation.ValidationIssue;
import com.example.dam.metadata.domain.value.MetadataValue;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Deterministic metadata CRUD. Works independently of any AI capability. */
@Service
public class MetadataService implements InitialMetadataImporter {

    private static final Set<MetadataSource> IMPORTABLE_SOURCES =
            Set.of(MetadataSource.MANUAL, MetadataSource.DAM, MetadataSource.PRODUCT_SYSTEM, MetadataSource.EMBEDDED);
    private static final Actor METADATA_SERVICE = Actor.system("metadata-service");

    private final AssetRepository assetRepository;
    private final AssetMetadataRepository metadataRepository;
    private final MetadataSchemaService schemaService;
    private final MetadataValueParser parser;
    private final MetadataValueValidator valueValidator;
    private final MetadataWriter writer;
    private final AssetStatusService statusService;
    private final MetadataProposalRepository proposalRepository;
    private final Clock clock;

    public MetadataService(AssetRepository assetRepository, AssetMetadataRepository metadataRepository,
                           MetadataSchemaService schemaService, MetadataValueParser parser,
                           MetadataValueValidator valueValidator, MetadataWriter writer,
                           AssetStatusService statusService, MetadataProposalRepository proposalRepository,
                           Clock clock) {
        this.assetRepository = assetRepository;
        this.proposalRepository = proposalRepository;
        this.metadataRepository = metadataRepository;
        this.schemaService = schemaService;
        this.parser = parser;
        this.valueValidator = valueValidator;
        this.writer = writer;
        this.statusService = statusService;
        this.clock = clock;
    }

    /**
     * @param origins per field, the approved AI proposal the current value originates from (provenance details
     *                such as agent, explanation and evidence); absent for values that did not come from a proposal
     */
    public record AssetMetadataView(Asset asset, MetadataSchema schema, AssetMetadataSnapshot metadata,
                                    Map<String, MetadataProposal> origins) {
        public AssetMetadataView {
            origins = Map.copyOf(origins);
        }
    }

    @Transactional(readOnly = true)
    public AssetMetadataView getMetadata(UUID assetId) {
        Asset asset = assetRepository.findById(assetId).orElseThrow(() -> new AssetNotFoundException(assetId));
        AssetMetadataSnapshot snapshot = snapshot(asset.id());
        Map<String, MetadataProposal> origins = new HashMap<>();
        for (MetadataProposal proposal : proposalRepository.findLatestApprovedByAsset(asset.id())) {
            snapshot.value(proposal.fieldKey())
                    .filter(current -> current.isEquivalentTo(proposal.effectiveValue()))
                    .ifPresent(current -> origins.put(proposal.fieldKey(), proposal));
        }
        return new AssetMetadataView(asset, schemaService.schemaFor(asset.type()), snapshot, origins);
    }

    @Transactional
    public AssetMetadata setValue(UUID assetId, String fieldKey, Object rawValue, String reason, Actor actor) {
        Asset asset = lock(assetId);
        MetadataFieldDefinition field = requireField(schemaService.schemaFor(asset.type()), fieldKey);
        MetadataValue value = parseAndValidate(field, rawValue);
        Optional<AssetMetadata> previous = metadataRepository.findByAssetId(assetId).stream()
                .filter(entry -> entry.fieldKey().equals(fieldKey))
                .findFirst();
        Instant now = clock.instant();
        AssetMetadata next = previous
                .map(existing -> existing.replacedBy(value, MetadataSource.MANUAL, null, MetadataStatus.APPROVED,
                        null, actor, now))
                .orElseGet(() -> AssetMetadata.create(assetId, fieldKey, value, MetadataSource.MANUAL, null,
                        MetadataStatus.APPROVED, null, actor, now));

        writer.supersedeOpenProposals(assetId, fieldKey, METADATA_SERVICE, "Superseded by manual update from " + actor);
        writer.apply(asset, List.of(new MetadataWriter.Change(fieldKey, previous.orElse(null), next,
                AuditAction.VALUE_SET_MANUALLY, actor, null, null, reason)));
        statusService.refresh(asset);
        return next;
    }

    @Transactional
    public void removeValue(UUID assetId, String fieldKey, String reason, Actor actor) {
        Asset asset = lock(assetId);
        AssetMetadata previous = snapshot(assetId).entry(fieldKey)
                .orElseThrow(() -> new ResourceNotFoundException("METADATA_FIELD_NOT_SET",
                        "Field '%s' has no value on asset %s".formatted(fieldKey, assetId)));
        writer.supersedeOpenProposals(assetId, fieldKey, METADATA_SERVICE, "Superseded by manual removal from " + actor);
        writer.apply(asset, List.of(new MetadataWriter.Change(fieldKey, previous, null, AuditAction.VALUE_REMOVED,
                actor, null, null, reason)));
        statusService.refresh(asset);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void importInitialMetadata(Asset asset, Map<String, Object> rawValues, String source, Actor actor) {
        MetadataSource metadataSource = parseImportSource(source);
        MetadataSchema schema = schemaService.schemaFor(asset.type());
        List<ValidationIssue> issues = new ArrayList<>();
        List<MetadataWriter.Change> changes = new ArrayList<>();
        Instant now = clock.instant();
        MetadataStatus status = metadataSource == MetadataSource.EMBEDDED ? MetadataStatus.VALIDATED : MetadataStatus.APPROVED;

        rawValues.forEach((fieldKey, rawValue) -> {
            Optional<MetadataFieldDefinition> field = schema.field(fieldKey);
            if (field.isEmpty()) {
                issues.add(ValidationIssue.error(IssueCode.UNSUPPORTED_FIELD,
                        fieldKey, "Field '%s' is not defined in the metadata schema".formatted(fieldKey)));
                return;
            }
            ParseResult parsed = parser.parse(field.get(), rawValue);
            if (!parsed.isSuccess()) {
                issues.addAll(parsed.issues());
                return;
            }
            List<ValidationIssue> valueIssues = valueValidator.validate(field.get(), parsed.value()).stream()
                    .filter(ValidationIssue::isError).toList();
            if (!valueIssues.isEmpty()) {
                issues.addAll(valueIssues);
                return;
            }
            AssetMetadata entry = AssetMetadata.create(asset.id(), fieldKey, parsed.value(), metadataSource, null,
                    status, null, actor, now);
            changes.add(new MetadataWriter.Change(fieldKey, null, entry, AuditAction.VALUE_IMPORTED, actor, null, null,
                    "Initial metadata at registration (" + metadataSource + ")"));
        });
        if (!issues.isEmpty()) {
            throw new MetadataValidationException(issues);
        }
        writer.apply(asset, changes);
        statusService.refresh(asset);
    }

    private Asset lock(UUID assetId) {
        return assetRepository.lockById(assetId).orElseThrow(() -> new AssetNotFoundException(assetId));
    }

    private AssetMetadataSnapshot snapshot(UUID assetId) {
        return AssetMetadataSnapshot.of(assetId, metadataRepository.findByAssetId(assetId));
    }

    private MetadataValue parseAndValidate(MetadataFieldDefinition field, Object rawValue) {
        MetadataValue value = parser.parse(field, rawValue).valueOrThrow();
        List<ValidationIssue> errors = valueValidator.validate(field, value).stream()
                .filter(ValidationIssue::isError).toList();
        if (!errors.isEmpty()) {
            throw new MetadataValidationException(errors);
        }
        return value;
    }

    private static MetadataFieldDefinition requireField(MetadataSchema schema, String fieldKey) {
        return schema.field(fieldKey).orElseThrow(() -> new InvalidRequestException("UNSUPPORTED_FIELD",
                "Field '%s' is not defined in metadata schema v%d".formatted(fieldKey, schema.version())));
    }

    private static MetadataSource parseImportSource(String source) {
        MetadataSource parsed;
        try {
            parsed = source == null ? MetadataSource.MANUAL : MetadataSource.valueOf(source.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("INVALID_METADATA_SOURCE", "Unknown metadata source: " + source);
        }
        if (!IMPORTABLE_SOURCES.contains(parsed)) {
            throw new InvalidRequestException("INVALID_METADATA_SOURCE",
                    "Initial metadata may not claim source " + parsed + "; allowed: " + IMPORTABLE_SOURCES);
        }
        return parsed;
    }
}

package com.example.dam.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.dam.asset.domain.Asset;
import com.example.dam.asset.domain.AssetRepository;
import com.example.dam.asset.domain.AssetType;
import com.example.dam.audit.domain.MetadataAuditRepository;
import com.example.dam.common.domain.Actor;
import com.example.dam.common.domain.ConflictException;
import com.example.dam.metadata.domain.AssetMetadata;
import com.example.dam.metadata.domain.AssetMetadataRepository;
import com.example.dam.metadata.domain.MetadataSource;
import com.example.dam.metadata.domain.MetadataStatus;
import com.example.dam.metadata.domain.audit.AuditAction;
import com.example.dam.metadata.domain.audit.MetadataAuditEvent;
import com.example.dam.metadata.domain.proposal.ApprovalRoute;
import com.example.dam.metadata.domain.proposal.MetadataProposal;
import com.example.dam.metadata.domain.proposal.MetadataProposalRepository;
import com.example.dam.metadata.domain.proposal.ProposalQuery;
import com.example.dam.metadata.domain.value.BooleanValue;
import com.example.dam.metadata.domain.value.DateValue;
import com.example.dam.metadata.domain.value.ListValue;
import com.example.dam.metadata.domain.value.MetadataValue;
import com.example.dam.metadata.domain.value.NumberValue;
import com.example.dam.metadata.domain.value.TextValue;
import com.example.dam.support.AbstractIntegrationTest;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** JDBC adapters against a real PostgreSQL: JSONB typing, proposal lifecycle and append-only audit. */
class PersistenceIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    private AssetRepository assets;
    @Autowired
    private AssetMetadataRepository metadata;
    @Autowired
    private MetadataProposalRepository proposals;
    @Autowired
    private MetadataAuditRepository audit;

    @Test
    void metadataValuesRoundTripWithTheirTypes() {
        Asset asset = newAsset();
        Map<String, MetadataValue> values = Map.of(
                "usageRightsEnd", new DateValue(LocalDate.of(2027, 12, 31)),
                "modelPresent", new BooleanValue(true),
                "modelCount", NumberValue.of(2),
                "keywords", ListValue.ofTexts(List.of("shirt", "blue")),
                "title", new TextValue("Oxford Shirt"));
        values.forEach((key, value) -> metadata.upsert(AssetMetadata.create(asset.id(), key, value,
                MetadataSource.MANUAL, null, MetadataStatus.APPROVED, null, Actor.human("tester"), NOW)));

        Map<String, MetadataValue> stored = metadata.findByAssetId(asset.id()).stream()
                .collect(Collectors.toMap(AssetMetadata::fieldKey, AssetMetadata::value));

        assertThat(stored).isEqualTo(values);
        assertThat(metadata.findByAssetIds(List.of(asset.id())).get(asset.id())).hasSize(5);
        assertThat(metadata.delete(asset.id(), "title")).isTrue();
        assertThat(metadata.delete(asset.id(), "title")).isFalse();
    }

    @Test
    void upsertKeepsCreationTimeAndReplacesProvenance() {
        Asset asset = newAsset();
        AssetMetadata first = AssetMetadata.create(asset.id(), "color", new TextValue("BLUE"),
                MetadataSource.AI_GENERATED, 0.95, MetadataStatus.VALIDATED, "demo:model", Actor.ai("agent"), NOW);
        metadata.upsert(first);
        metadata.upsert(first.replacedBy(new TextValue("NAVY"), MetadataSource.MANUAL, null, MetadataStatus.APPROVED,
                null, Actor.human("editor"), NOW.plusSeconds(60)));

        AssetMetadata stored = metadata.findByAssetId(asset.id()).getFirst();
        assertThat(stored.value()).isEqualTo(new TextValue("NAVY"));
        assertThat(stored.source()).isEqualTo(MetadataSource.MANUAL);
        assertThat(stored.confidence()).isNull();
        assertThat(stored.createdAt()).isEqualTo(NOW);
        assertThat(stored.updatedBy()).isEqualTo("editor");
    }

    @Test
    void proposalDecisionsAreOptimisticallyGuarded() {
        Asset asset = newAsset();
        MetadataProposal proposal = new MetadataProposal(UUID.randomUUID(), asset.id(), "angleFacing",
                new TextValue("FRONT"), null, 0.95, "Orientation detected", List.of("imageAnalysis.orientation=front"),
                MetadataSource.AI_GENERATED, "metadataExtractionAgent", "demo:model", UUID.randomUUID(),
                ApprovalRoute.HUMAN_REVIEW, "Schema requires human approval", MetadataStatus.PROPOSED, NOW, null, null,
                null, null);
        proposals.insert(proposal);

        assertThat(proposals.find(new ProposalQuery(MetadataStatus.PROPOSED, ApprovalRoute.HUMAN_REVIEW, asset.id(), 10)))
                .singleElement().satisfies(found -> {
                    assertThat(found.proposedValue()).isEqualTo(new TextValue("FRONT"));
                    assertThat(found.evidence()).containsExactly("imageAnalysis.orientation=front");
                });
        assertThat(proposals.countOpenByAssetAndRoute(asset.id(), ApprovalRoute.HUMAN_REVIEW)).isEqualTo(1);

        MetadataProposal approved = proposal.approve(Actor.human("reviewer"), "ok", NOW);
        proposals.updateDecision(approved);
        assertThat(proposals.findById(proposal.id()).orElseThrow().status()).isEqualTo(MetadataStatus.APPROVED);
        assertThatThrownBy(() -> proposals.updateDecision(proposal.reject(Actor.human("other"), "late", NOW)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void auditHistoryIsReturnedNewestFirst() {
        Asset asset = newAsset();
        Function<Integer, MetadataAuditEvent> event = minutes -> new MetadataAuditEvent(UUID.randomUUID(), asset.id(),
                "color", AuditAction.VALUE_SET_MANUALLY, null, new TextValue("BLUE"), MetadataSource.MANUAL, null, null,
                "tester", null, null, "minute " + minutes, "corr", NOW.plusSeconds(60L * minutes));
        audit.append(event.apply(1));
        audit.append(event.apply(3));
        audit.append(event.apply(2));

        assertThat(audit.findByAssetId(asset.id(), 10)).extracting(MetadataAuditEvent::reason)
                .containsExactly("minute 3", "minute 2", "minute 1");
    }

    private Asset newAsset() {
        Asset asset = Asset.register(uniqueId("PERS"), "shirt.jpg", "image/jpeg",
                URI.create("https://dam.example.com/shirt.jpg"), AssetType.PRODUCT_IMAGE, NOW);
        assets.insert(asset);
        return asset;
    }
}

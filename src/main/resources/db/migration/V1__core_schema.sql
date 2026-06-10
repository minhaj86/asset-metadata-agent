-- Core schema for the DAM metadata POC. Flyway owns the schema; Hibernate/LangChain4j auto-DDL is never used.
-- ${embeddingdim} is a Flyway placeholder bound to ai.embedding.dimension.

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------------------------
-- Assets (references to binaries owned by the external DAM)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE assets (
    id                 UUID PRIMARY KEY,
    external_asset_id  VARCHAR(100)  NOT NULL,
    filename           VARCHAR(512)  NOT NULL,
    mime_type          VARCHAR(127)  NOT NULL,
    asset_url          VARCHAR(2048) NOT NULL,
    asset_type         VARCHAR(40)   NOT NULL,
    status             VARCHAR(40)   NOT NULL,
    metadata_revision  BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_assets_external_asset_id UNIQUE (external_asset_id)
);
CREATE INDEX idx_assets_type ON assets (asset_type);

-- ---------------------------------------------------------------------------------------------
-- Metadata schema (seeded from metadata-schema/*.yaml at startup)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE metadata_schema_info (
    singleton       BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    schema_version  INT         NOT NULL,
    synced_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE metadata_field_definitions (
    field_key                VARCHAR(100) PRIMARY KEY,
    display_name             VARCHAR(200) NOT NULL,
    data_type                VARCHAR(30)  NOT NULL,
    required                 BOOLEAN      NOT NULL,
    repeatable               BOOLEAN      NOT NULL,
    searchable               BOOLEAN      NOT NULL,
    semantic_search          BOOLEAN      NOT NULL,
    ai_enrichable            BOOLEAN      NOT NULL,
    ai_auto_apply            BOOLEAN      NOT NULL,
    human_approval_required  BOOLEAN      NOT NULL,
    allowed_values           JSONB        NOT NULL DEFAULT '[]'::jsonb,
    validation_rules         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    description              TEXT,
    position                 INT          NOT NULL
);

CREATE TABLE metadata_asset_type_requirements (
    asset_type   VARCHAR(40)  NOT NULL,
    field_key    VARCHAR(100) NOT NULL REFERENCES metadata_field_definitions (field_key) ON DELETE CASCADE,
    requirement  VARCHAR(20)  NOT NULL,
    PRIMARY KEY (asset_type, field_key)
);

CREATE TABLE metadata_cross_field_rules (
    rule_id     VARCHAR(100) PRIMARY KEY,
    rule_type   VARCHAR(40)  NOT NULL,
    definition  JSONB        NOT NULL
);

-- ---------------------------------------------------------------------------------------------
-- Current metadata values (one row per asset and field)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE asset_metadata (
    asset_id    UUID         NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    field_key   VARCHAR(100) NOT NULL,
    value       JSONB        NOT NULL,
    source      VARCHAR(30)  NOT NULL,
    confidence  NUMERIC(4, 3),
    status      VARCHAR(20)  NOT NULL,
    ai_model    VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  VARCHAR(200) NOT NULL,
    PRIMARY KEY (asset_id, field_key),
    CONSTRAINT ck_asset_metadata_status CHECK (status IN ('APPROVED', 'VALIDATED'))
);
CREATE INDEX idx_asset_metadata_field ON asset_metadata (field_key);
CREATE INDEX idx_asset_metadata_value ON asset_metadata USING gin (value jsonb_path_ops);

-- ---------------------------------------------------------------------------------------------
-- AI proposals and their decisions
-- ---------------------------------------------------------------------------------------------
CREATE TABLE metadata_proposals (
    id                UUID PRIMARY KEY,
    asset_id          UUID          NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    field_key         VARCHAR(100)  NOT NULL,
    proposed_value    JSONB         NOT NULL,
    current_value     JSONB,
    confidence        NUMERIC(4, 3) NOT NULL,
    explanation       TEXT,
    evidence          JSONB         NOT NULL DEFAULT '[]'::jsonb,
    source            VARCHAR(30)   NOT NULL,
    proposed_by       VARCHAR(200),
    ai_model          VARCHAR(200),
    workflow_run_id   UUID,
    route             VARCHAR(30)   NOT NULL,
    route_reason      TEXT,
    status            VARCHAR(20)   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    decided_at        TIMESTAMPTZ,
    decided_by        VARCHAR(200),
    decision_comment  TEXT,
    CONSTRAINT ck_proposal_status CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED'))
);
CREATE INDEX idx_proposals_status_route ON metadata_proposals (status, route, created_at DESC);
CREATE INDEX idx_proposals_asset ON metadata_proposals (asset_id, field_key);
-- At most one open proposal per asset and field; newer proposals supersede older ones.
CREATE UNIQUE INDEX uq_proposals_open_per_field ON metadata_proposals (asset_id, field_key) WHERE status = 'PROPOSED';

-- ---------------------------------------------------------------------------------------------
-- Taxonomy
-- ---------------------------------------------------------------------------------------------
CREATE TABLE taxonomy_nodes (
    id           VARCHAR(200)  PRIMARY KEY,
    parent_id    VARCHAR(200)  REFERENCES taxonomy_nodes (id),
    name         VARCHAR(200)  NOT NULL,
    path         VARCHAR(1000) NOT NULL UNIQUE,
    depth        INT           NOT NULL,
    synonyms     TEXT[]        NOT NULL DEFAULT '{}',
    description  TEXT
);
CREATE INDEX idx_taxonomy_parent ON taxonomy_nodes (parent_id);

-- ---------------------------------------------------------------------------------------------
-- Audit (append-only; intentionally no FK so history survives asset removal)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE metadata_audit_events (
    id              UUID PRIMARY KEY,
    asset_id        UUID          NOT NULL,
    field_key       VARCHAR(100)  NOT NULL,
    action          VARCHAR(40)   NOT NULL,
    previous_value  JSONB,
    new_value       JSONB,
    source          VARCHAR(30),
    ai_model        VARCHAR(200),
    confidence      NUMERIC(4, 3),
    actor           VARCHAR(200)  NOT NULL,
    approved_by     VARCHAR(200),
    proposal_id     UUID,
    reason          TEXT,
    correlation_id  VARCHAR(100),
    occurred_at     TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_audit_asset_time ON metadata_audit_events (asset_id, occurred_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Semantic search: one embedding per asset, built from semantic-search-enabled metadata
-- ---------------------------------------------------------------------------------------------
CREATE TABLE asset_embeddings (
    asset_id           UUID PRIMARY KEY REFERENCES assets (id) ON DELETE CASCADE,
    embedding          vector(${embeddingdim}) NOT NULL,
    embedding_text     TEXT         NOT NULL,
    embedding_model    VARCHAR(200) NOT NULL,
    metadata_revision  BIGINT       NOT NULL,
    indexed_at         TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_asset_embeddings_hnsw ON asset_embeddings USING hnsw (embedding vector_cosine_ops);

-- ---------------------------------------------------------------------------------------------
-- RAG knowledge base
-- ---------------------------------------------------------------------------------------------
CREATE TABLE knowledge_documents (
    id               VARCHAR(200)  PRIMARY KEY,
    title            VARCHAR(500)  NOT NULL,
    source           VARCHAR(1000) NOT NULL,
    content_hash     VARCHAR(64)   NOT NULL,
    chunk_count      INT           NOT NULL,
    embedding_model  VARCHAR(200)  NOT NULL,
    ingested_at      TIMESTAMPTZ   NOT NULL
);

-- Layout expected by LangChain4j's PgVectorEmbeddingStore (COMBINED_JSONB metadata mode, createTable=false).
CREATE TABLE knowledge_embeddings (
    embedding_id  UUID PRIMARY KEY,
    embedding     vector(${embeddingdim}),
    text          TEXT NULL,
    metadata      JSONB NULL
);
CREATE INDEX idx_knowledge_embeddings_hnsw ON knowledge_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_knowledge_embeddings_document ON knowledge_embeddings ((metadata ->> 'documentId'));

-- UI support. All changes are additive: presentation metadata of the schema, the value applied when a reviewer
-- edits a proposal, tracked enrichment workflow runs and a derived per-asset metadata summary (read model).

-- Presentation groups of the configurable metadata schema (e.g. PRODUCT, VISUAL, RIGHTS)
CREATE TABLE metadata_field_groups (
    group_key     VARCHAR(40)  PRIMARY KEY,
    display_name  VARCHAR(100) NOT NULL,
    position      INT          NOT NULL
);

ALTER TABLE metadata_field_definitions ADD COLUMN field_group VARCHAR(40) NOT NULL DEFAULT 'OTHER';

-- Value applied when a reviewer edited the proposed value before approving it (NULL: proposed value applied as is)
ALTER TABLE metadata_proposals ADD COLUMN applied_value JSONB;

CREATE INDEX idx_proposals_workflow_run ON metadata_proposals (workflow_run_id);

-- Enrichment workflow runs: lifecycle, per-stage progress and outcome summary
CREATE TABLE enrichment_runs (
    id              UUID PRIMARY KEY,
    asset_id        UUID         NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    status          VARCHAR(20)  NOT NULL,
    requested_by    VARCHAR(200) NOT NULL,
    ai_model        VARCHAR(200),
    correlation_id  VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    stages          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    summary         JSONB,
    warnings        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    error_code      VARCHAR(100),
    error_message   TEXT,
    CONSTRAINT ck_enrichment_run_status CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_enrichment_runs_created ON enrichment_runs (created_at DESC);
CREATE INDEX idx_enrichment_runs_asset ON enrichment_runs (asset_id, created_at DESC);

-- Derived metadata summary per asset. Maintained in the same transaction as every metadata change (and refreshed
-- periodically for date-dependent findings such as expiring rights); it is never a source of truth.
CREATE TABLE asset_metadata_summaries (
    asset_id                UUID PRIMARY KEY REFERENCES assets (id) ON DELETE CASCADE,
    schema_version          INT           NOT NULL,
    valid                   BOOLEAN       NOT NULL,
    completeness            NUMERIC(5, 2) NOT NULL,
    required_completeness   NUMERIC(5, 2) NOT NULL,
    quality_score           INT           NOT NULL,
    missing_required        JSONB         NOT NULL DEFAULT '[]'::jsonb,
    missing_required_count  INT           NOT NULL,
    error_count             INT           NOT NULL,
    warning_count           INT           NOT NULL,
    ai_value_count          INT           NOT NULL,
    pending_review_count    INT           NOT NULL,
    low_confidence_count    INT           NOT NULL,
    usage_rights_end        DATE,
    refreshed_at            TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_summaries_completeness ON asset_metadata_summaries (completeness);
CREATE INDEX idx_summaries_quality ON asset_metadata_summaries (quality_score);
CREATE INDEX idx_summaries_rights_end ON asset_metadata_summaries (usage_rights_end);

CREATE INDEX idx_audit_occurred ON metadata_audit_events (occurred_at DESC);

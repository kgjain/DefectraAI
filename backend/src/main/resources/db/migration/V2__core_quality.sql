-- V2: products, batches, inspections, images, ML processing, human reviews
CREATE TABLE products (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(40)   NOT NULL,
    name           VARCHAR(160)  NOT NULL,
    category       VARCHAR(80)   NOT NULL,
    description    VARCHAR(2000),
    specifications JSONB,
    status         VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_by     BIGINT        REFERENCES users (id),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_code UNIQUE (code),
    CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_products_code_format CHECK (code ~ '^[A-Za-z0-9][A-Za-z0-9._-]{1,39}$')
);
CREATE INDEX ix_products_category ON products (category);
CREATE INDEX ix_products_status ON products (status);
CREATE INDEX ix_products_name_trgm ON products (lower(name));

CREATE TABLE batches (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(40)   NOT NULL,
    product_id       BIGINT        NOT NULL REFERENCES products (id),
    production_date  DATE          NOT NULL,
    quantity_produced INT          NOT NULL,
    production_line  VARCHAR(80),
    supplier         VARCHAR(160),
    notes            VARCHAR(2000),
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_by       BIGINT        REFERENCES users (id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_batches_code UNIQUE (code),
    CONSTRAINT ck_batches_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_batches_quantity CHECK (quantity_produced >= 0),
    CONSTRAINT ck_batches_code_format CHECK (code ~ '^[A-Za-z0-9][A-Za-z0-9._-]{1,39}$')
);
CREATE INDEX ix_batches_product ON batches (product_id);
CREATE INDEX ix_batches_status ON batches (status);
CREATE INDEX ix_batches_production_date ON batches (production_date);

CREATE TABLE inspections (
    id                    BIGSERIAL PRIMARY KEY,
    batch_id              BIGINT        NOT NULL REFERENCES batches (id),
    product_id            BIGINT        NOT NULL REFERENCES products (id),
    item_reference        VARCHAR(120)  NOT NULL,
    -- normalized key: distinct-item counting and duplicate protection
    item_key              VARCHAR(120)  GENERATED ALWAYS AS (upper(btrim(item_reference))) STORED,
    evidence_scope        VARCHAR(20)   NOT NULL,
    inspection_type       VARCHAR(16)   NOT NULL DEFAULT 'INITIAL',
    status                VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    observations          VARCHAR(4000),
    inspector_id          BIGINT        REFERENCES users (id),
    -- set for FOLLOW_UP rows; FK added in V3 after corrective_actions exists
    corrective_action_id  BIGINT,
    parent_inspection_id  BIGINT        REFERENCES inspections (id),
    idempotency_key       VARCHAR(128),
    submitted_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_inspections_scope CHECK (evidence_scope IN ('ITEM', 'SURFACE', 'BATCH_SAMPLE')),
    CONSTRAINT ck_inspections_type CHECK (inspection_type IN ('INITIAL', 'FOLLOW_UP')),
    CONSTRAINT ck_inspections_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'CANCELLED')),
    CONSTRAINT uq_inspections_idem UNIQUE (idempotency_key)
);
-- One initial inspection per physical item per batch (duplicate-submission guard).
-- Follow-ups and cancelled rows are exempt so history can grow.
CREATE UNIQUE INDEX uq_inspection_initial_item
    ON inspections (batch_id, item_key)
    WHERE inspection_type = 'INITIAL' AND status <> 'CANCELLED';
CREATE INDEX ix_inspections_batch ON inspections (batch_id);
CREATE INDEX ix_inspections_product ON inspections (product_id);
CREATE INDEX ix_inspections_inspector ON inspections (inspector_id);
CREATE INDEX ix_inspections_status ON inspections (status);
CREATE INDEX ix_inspections_submitted ON inspections (submitted_at);
CREATE INDEX ix_inspections_action ON inspections (corrective_action_id);

CREATE TABLE inspection_images (
    id               BIGSERIAL PRIMARY KEY,
    inspection_id    BIGINT        NOT NULL REFERENCES inspections (id) ON DELETE CASCADE,
    storage_key      VARCHAR(300)  NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type     VARCHAR(80)   NOT NULL,
    size_bytes       BIGINT        NOT NULL,
    sha256           VARCHAR(64)   NOT NULL,
    version          INT           NOT NULL DEFAULT 1,
    is_current       BOOLEAN       NOT NULL DEFAULT TRUE,
    replaced_by_id   BIGINT        REFERENCES inspection_images (id),
    uploaded_by      BIGINT        REFERENCES users (id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_images_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_images_content_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_images_size CHECK (size_bytes > 0),
    CONSTRAINT ck_images_version CHECK (version >= 1)
);
CREATE INDEX ix_images_inspection ON inspection_images (inspection_id);
CREATE INDEX ix_images_current ON inspection_images (inspection_id, is_current);

CREATE TABLE processing_attempts (
    id             BIGSERIAL PRIMARY KEY,
    image_id       BIGINT       NOT NULL REFERENCES inspection_images (id) ON DELETE CASCADE,
    attempt_no     INT          NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    error_details  TEXT,
    duration_ms    INT,
    model_name     VARCHAR(80),
    model_version  VARCHAR(40),
    requested_by   BIGINT       REFERENCES users (id),
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_attempt_no UNIQUE (image_id, attempt_no),
    CONSTRAINT ck_attempt_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);
CREATE INDEX ix_attempts_image ON processing_attempts (image_id);
CREATE INDEX ix_attempts_status ON processing_attempts (status);

CREATE TABLE model_results (
    id              BIGSERIAL PRIMARY KEY,
    image_id        BIGINT        NOT NULL REFERENCES inspection_images (id) ON DELETE CASCADE,
    attempt_id      BIGINT        REFERENCES processing_attempts (id),
    predicted_class VARCHAR(30)   NOT NULL,
    score           NUMERIC(6,4),
    score_type      VARCHAR(40),
    model_name      VARCHAR(80)   NOT NULL,
    model_version   VARCHAR(40)   NOT NULL,
    processed_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    duration_ms     INT           NOT NULL,
    is_current      BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_model_results_class CHECK (predicted_class IN ('GOOD', 'POTENTIALLY_DEFECTIVE')),
    CONSTRAINT ck_model_results_score CHECK (score IS NULL OR (score >= 0 AND score <= 1)),
    -- a missing score must stay NULL: never stored as 1.0 / 100%
    CONSTRAINT ck_model_results_score_type CHECK ((score IS NOT NULL AND score_type IS NOT NULL) OR score IS NULL)
);
CREATE INDEX ix_model_results_image ON model_results (image_id);
CREATE INDEX ix_model_results_current ON model_results (image_id, is_current);
CREATE INDEX ix_model_results_processed ON model_results (processed_at);

CREATE TABLE inspection_reviews (
    id               BIGSERIAL PRIMARY KEY,
    inspection_id    BIGINT       NOT NULL REFERENCES inspections (id) ON DELETE CASCADE,
    model_result_id  BIGINT       REFERENCES model_results (id),
    reviewer_id      BIGINT       NOT NULL REFERENCES users (id),
    decision         VARCHAR(30)  NOT NULL,
    confirmed_outcome VARCHAR(30),
    reason           VARCHAR(2000),
    evidence_requested_note VARCHAR(1000),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_reviews_decision CHECK (decision IN ('CONFIRM', 'CORRECT', 'MANUAL_REQUIRED', 'EVIDENCE_REQUESTED')),
    CONSTRAINT ck_reviews_outcome CHECK (confirmed_outcome IS NULL OR confirmed_outcome IN ('GOOD', 'POTENTIALLY_DEFECTIVE', 'NEEDS_MANUAL')),
    CONSTRAINT ck_reviews_reason_required CHECK (
        decision = 'CONFIRM' OR (reason IS NOT NULL AND btrim(reason) <> '')
    )
);
CREATE INDEX ix_reviews_inspection ON inspection_reviews (inspection_id);
CREATE INDEX ix_reviews_result ON inspection_reviews (model_result_id);
CREATE INDEX ix_reviews_reviewer ON inspection_reviews (reviewer_id);

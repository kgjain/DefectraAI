-- V3: defects, evidence links, corrective actions, batch decisions, AI reports
-- (adds the inspections.corrective_action_id FK deferred from V2)

CREATE TABLE defects (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(20)   NOT NULL,
    inspection_id       BIGINT        NOT NULL REFERENCES inspections (id),
    batch_id            BIGINT        NOT NULL REFERENCES batches (id),
    product_id          BIGINT        NOT NULL REFERENCES products (id),
    item_key            VARCHAR(120)  NOT NULL,
    category            VARCHAR(60),
    description         VARCHAR(2000) NOT NULL,
    severity            VARCHAR(16)   NOT NULL,
    review_status       VARCHAR(20)   NOT NULL DEFAULT 'CONFIRMED',
    status              VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    confirmation_reason VARCHAR(2000),
    assigned_reviewer_id BIGINT       REFERENCES users (id),
    created_by          BIGINT        REFERENCES users (id),
    closed_by           BIGINT        REFERENCES users (id),
    closed_at           TIMESTAMPTZ,
    closure_reason      VARCHAR(2000),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_defects_code UNIQUE (code),
    CONSTRAINT ck_defects_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_defects_status CHECK (status IN ('OPEN', 'CLOSED', 'REOPENED')),
    CONSTRAINT ck_defects_review_status CHECK (review_status IN ('PENDING', 'CONFIRMED', 'DISPUTED')),
    CONSTRAINT ck_defects_reason_when_closed CHECK (status <> 'CLOSED' OR (closure_reason IS NOT NULL AND btrim(closure_reason) <> ''))
);
CREATE INDEX ix_defects_inspection ON defects (inspection_id);
CREATE INDEX ix_defects_batch ON defects (batch_id);
CREATE INDEX ix_defects_product ON defects (product_id);
CREATE INDEX ix_defects_status ON defects (status);
CREATE INDEX ix_defects_severity ON defects (severity);
CREATE INDEX ix_defects_item ON defects (batch_id, item_key);

CREATE TABLE defect_evidence (
    defect_id BIGINT NOT NULL REFERENCES defects (id) ON DELETE CASCADE,
    image_id  BIGINT NOT NULL REFERENCES inspection_images (id),
    note      VARCHAR(500),
    added_by  BIGINT REFERENCES users (id),
    added_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (defect_id, image_id)
);

CREATE TABLE corrective_actions (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(20)   NOT NULL,
    defect_id           BIGINT        NOT NULL REFERENCES defects (id),
    title               VARCHAR(200)  NOT NULL,
    description         VARCHAR(4000),
    owner_id            BIGINT        NOT NULL REFERENCES users (id),
    target_date         DATE          NOT NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'OPEN',
    work_notes          VARCHAR(4000),
    completion_details  VARCHAR(4000),
    status_reason       VARCHAR(2000),
    created_by          BIGINT        REFERENCES users (id),
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_actions_code UNIQUE (code),
    CONSTRAINT ck_actions_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'AWAITING_REINSPECTION', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_actions_target_date CHECK (target_date >= current_date - INTERVAL '3650 days')
);
CREATE INDEX ix_actions_defect ON corrective_actions (defect_id);
CREATE INDEX ix_actions_owner ON corrective_actions (owner_id);
CREATE INDEX ix_actions_status ON corrective_actions (status);
CREATE INDEX ix_actions_target ON corrective_actions (target_date);

-- follow-up inspections point back at the action that triggered them
ALTER TABLE inspections
    ADD CONSTRAINT fk_inspections_corrective_action
        FOREIGN KEY (corrective_action_id) REFERENCES corrective_actions (id);

CREATE TABLE batch_decisions (
    id          BIGSERIAL PRIMARY KEY,
    batch_id    BIGINT       NOT NULL REFERENCES batches (id),
    decision    VARCHAR(30)  NOT NULL,
    reason      VARCHAR(2000) NOT NULL,
    decided_by  BIGINT       NOT NULL REFERENCES users (id),
    decided_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_decisions_decision CHECK (decision IN ('HOLD', 'RELEASE', 'CONDITIONAL_RELEASE'))
);
CREATE INDEX ix_decisions_batch ON batch_decisions (batch_id, decided_at DESC);

CREATE TABLE ai_reports (
    id             BIGSERIAL PRIMARY KEY,
    report_type    VARCHAR(40)   NOT NULL,
    scope          JSONB         NOT NULL,
    request_text   VARCHAR(4000) NOT NULL,
    response_text  TEXT,
    grounding_json JSONB,
    facts          JSONB,
    suggestions    JSONB,
    mode           VARCHAR(20)   NOT NULL DEFAULT 'llm',
    model_provider VARCHAR(40),
    model_name     VARCHAR(80),
    status         VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    error_details  TEXT,
    created_by     BIGINT        REFERENCES users (id),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_reports_type CHECK (report_type IN ('BATCH_SUMMARY', 'QUALITY_REPORT', 'QUESTION')),
    CONSTRAINT ck_reports_mode CHECK (mode IN ('llm', 'dev-no-llm')),
    CONSTRAINT ck_reports_status CHECK (status IN ('COMPLETED', 'FAILED'))
);
CREATE INDEX ix_reports_created ON ai_reports (created_at DESC);
CREATE INDEX ix_reports_type ON ai_reports (report_type);

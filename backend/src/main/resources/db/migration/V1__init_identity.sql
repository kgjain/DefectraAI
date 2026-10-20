-- V1: identity, roles, token lifecycle, idempotency
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    org_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    email         VARCHAR(160),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT ck_users_username_format CHECK (username ~ '^[a-zA-Z0-9._-]{3,64}$')
);

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(32) NOT NULL,
    description VARCHAR(200),
    CONSTRAINT uq_roles_code UNIQUE (code)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

CREATE TABLE refresh_tokens (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    replaced_by_id BIGINT REFERENCES refresh_tokens (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expiry ON refresh_tokens (expires_at);

-- Duplicate-submission protection: replay the stored response for a repeated key.
CREATE TABLE idempotency_records (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    idem_key        VARCHAR(128) NOT NULL,
    endpoint        VARCHAR(160) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    response_status INT,
    response_body   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency UNIQUE (user_id, idem_key)
);
CREATE INDEX ix_idempotency_created ON idempotency_records (created_at);

-- Lookup data (roles are configuration, not credentials)
INSERT INTO roles (code, description) VALUES
    ('INSPECTOR', 'Capture inspections, upload evidence, request analysis, review own inspections'),
    ('SUPERVISOR', 'Record corrective work, follow-up inspections, close defects'),
    ('QUALITY_MANAGER', 'Full quality oversight: assignments, disputed reviews, batch decisions, audit, reports');

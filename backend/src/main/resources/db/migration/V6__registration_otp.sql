ALTER TABLE users ADD COLUMN phone VARCHAR(32);

CREATE TABLE registration_otps (
    id          BIGSERIAL PRIMARY KEY,
    contact     VARCHAR(160) NOT NULL,
    code_hash   VARCHAR(100) NOT NULL,
    payload     VARCHAR(2000) NOT NULL,
    attempts    INT NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_registration_otps_contact ON registration_otps (contact);

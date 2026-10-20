-- V4: append-only audit trail
CREATE TABLE audit_events (
    id            BIGSERIAL PRIMARY KEY,
    entity_type   VARCHAR(40)   NOT NULL,
    entity_id     VARCHAR(40)   NOT NULL,
    action        VARCHAR(60)   NOT NULL,
    actor_id      BIGINT        REFERENCES users (id),
    actor_username VARCHAR(64),
    reason        VARCHAR(2000),
    details       JSONB,
    occurred_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON audit_events (entity_type, entity_id);
CREATE INDEX ix_audit_occurred ON audit_events (occurred_at DESC);
CREATE INDEX ix_audit_actor ON audit_events (actor_id);

-- Nobody may edit or delete audit history (enforced at the database level).
CREATE OR REPLACE FUNCTION forbid_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_mutation();

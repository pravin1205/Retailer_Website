-- Immutable audit log for all authentication events.
-- Never UPDATE or DELETE rows in this table.
CREATE TABLE identity.audit_log (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID,                           -- NULL for pre-auth failures
    tenant_id    UUID,
    event_type   VARCHAR(80)  NOT NULL,
    -- LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT,
    -- PASSWORD_CHANGED, TOKEN_REVOKED,
    -- ACCOUNT_DISABLED, ACCOUNT_ENABLED
    ip_address   INET,
    user_agent   VARCHAR(512),
    metadata     JSONB,                          -- extra context (e.g. attempt count)
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_user   ON identity.audit_log (user_id,   occurred_at DESC) WHERE user_id  IS NOT NULL;
CREATE INDEX idx_audit_tenant ON identity.audit_log (tenant_id, occurred_at DESC) WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_audit_type   ON identity.audit_log (event_type, occurred_at DESC);

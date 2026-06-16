CREATE TABLE identity.refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES identity.users(id),
    tenant_id   UUID,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    user_agent  VARCHAR(512),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_rt_user_id ON identity.refresh_tokens (user_id);
CREATE INDEX idx_rt_expires ON identity.refresh_tokens (expires_at) WHERE revoked_at IS NULL;

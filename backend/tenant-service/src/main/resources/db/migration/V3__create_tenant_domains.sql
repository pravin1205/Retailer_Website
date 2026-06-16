CREATE TABLE tenant.tenant_domains (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant.tenants(id),
    domain      VARCHAR(253) NOT NULL,
    is_primary  BOOLEAN      NOT NULL DEFAULT false,
    is_verified BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_tenant_domain UNIQUE (domain)
);

CREATE INDEX idx_td_domain ON tenant.tenant_domains (domain)    WHERE deleted_at IS NULL;
CREATE INDEX idx_td_tenant ON tenant.tenant_domains (tenant_id);

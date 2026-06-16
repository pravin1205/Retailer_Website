CREATE TABLE tenant.tenants (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                    VARCHAR(100) NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    tagline                 VARCHAR(500),
    description             TEXT,
    category                VARCHAR(100) NOT NULL,
    status                  VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    owner_user_id           UUID         NOT NULL,
    subscription_plan       VARCHAR(50)  NOT NULL DEFAULT 'FREE',
    subscription_expires_at TIMESTAMPTZ,
    logo_url                VARCHAR(2048),
    banner_url              VARCHAR(2048),
    accent_color            VARCHAR(30),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              UUID,
    updated_by              UUID,
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT uq_tenants_slug UNIQUE (slug)
);

CREATE INDEX idx_tenants_slug   ON tenant.tenants (slug)   WHERE deleted_at IS NULL;
CREATE INDEX idx_tenants_status ON tenant.tenants (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenants_owner  ON tenant.tenants (owner_user_id);

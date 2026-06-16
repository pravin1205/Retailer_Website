CREATE TABLE identity.roles (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE identity.user_tenant_roles (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES identity.users(id),
    tenant_id   UUID        NOT NULL,
    role_id     UUID        NOT NULL REFERENCES identity.roles(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_user_tenant_role UNIQUE (user_id, tenant_id, role_id)
);

CREATE INDEX idx_utr_user_tenant ON identity.user_tenant_roles (user_id, tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_utr_tenant      ON identity.user_tenant_roles (tenant_id)           WHERE deleted_at IS NULL;

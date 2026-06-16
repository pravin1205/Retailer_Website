CREATE TABLE customer.customers (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL,
    user_id        UUID          NOT NULL,
    first_name     VARCHAR(100),
    last_name      VARCHAR(100),
    phone          VARCHAR(20),
    email          VARCHAR(320),
    date_of_birth  DATE,
    loyalty_points INT           NOT NULL DEFAULT 0,
    tier           VARCHAR(30)   NOT NULL DEFAULT 'BRONZE',
    total_orders   INT           NOT NULL DEFAULT 0,
    total_spent    NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT uq_customer_tenant_user UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_cust_tenant  ON customer.customers (tenant_id)          WHERE deleted_at IS NULL;
CREATE INDEX idx_cust_user    ON customer.customers (user_id);
CREATE INDEX idx_cust_tier    ON customer.customers (tenant_id, tier)    WHERE deleted_at IS NULL;

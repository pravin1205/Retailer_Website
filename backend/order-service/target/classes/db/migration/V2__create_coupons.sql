CREATE TABLE order_data.coupons (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL,
    code            VARCHAR(50)   NOT NULL,
    description     VARCHAR(500),
    type            VARCHAR(20)   NOT NULL,       -- PERCENT | FLAT | FREE_DELIVERY
    value           NUMERIC(10,2) NOT NULL,
    min_order_value NUMERIC(12,2),
    max_discount    NUMERIC(12,2),
    usage_limit     INT,
    used_count      INT           NOT NULL DEFAULT 0,
    per_user_limit  INT           NOT NULL DEFAULT 1,
    valid_from      TIMESTAMPTZ   NOT NULL,
    valid_until     TIMESTAMPTZ,
    is_active       BOOLEAN       NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT uq_coupon_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_coupon_tenant ON order_data.coupons (tenant_id, code)
    WHERE deleted_at IS NULL AND is_active = true;

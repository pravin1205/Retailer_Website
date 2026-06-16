CREATE TABLE customer.addresses (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    customer_id UUID         NOT NULL REFERENCES customer.customers(id),
    label       VARCHAR(50),
    line1       VARCHAR(300) NOT NULL,
    line2       VARCHAR(300),
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100),
    pincode     VARCHAR(20)  NOT NULL,
    country     VARCHAR(100) NOT NULL DEFAULT 'IN',
    latitude    NUMERIC(9,6),
    longitude   NUMERIC(9,6),
    is_default  BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_addr_customer ON customer.addresses (customer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_addr_tenant   ON customer.addresses (tenant_id)   WHERE deleted_at IS NULL;

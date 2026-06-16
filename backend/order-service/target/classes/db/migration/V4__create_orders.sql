CREATE TABLE order_data.orders (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID          NOT NULL,
    customer_id          UUID          NOT NULL,
    order_number         VARCHAR(30)   NOT NULL,
    status               VARCHAR(30)   NOT NULL DEFAULT 'PLACED',
    subtotal             NUMERIC(12,2) NOT NULL,
    discount_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,
    delivery_charge      NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_amount           NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount         NUMERIC(12,2) NOT NULL,
    coupon_code          VARCHAR(50),
    delivery_slot        VARCHAR(100),
    delivery_address     JSONB         NOT NULL DEFAULT '{}',
    payment_method       VARCHAR(30)   NOT NULL,
    notes                TEXT,
    placed_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    confirmed_at         TIMESTAMPTZ,
    delivered_at         TIMESTAMPTZ,
    cancelled_at         TIMESTAMPTZ,
    cancellation_reason  VARCHAR(500),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by           UUID,
    CONSTRAINT uq_order_number UNIQUE (tenant_id, order_number)
);

CREATE INDEX idx_ord_tenant_status ON order_data.orders (tenant_id, status)
    WHERE cancelled_at IS NULL;
CREATE INDEX idx_ord_customer      ON order_data.orders (customer_id, placed_at DESC);
CREATE INDEX idx_ord_placed_at     ON order_data.orders (tenant_id, placed_at DESC);
CREATE INDEX idx_ord_number        ON order_data.orders (tenant_id, order_number);

CREATE TABLE order_data.order_items (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID          NOT NULL,
    order_id     UUID          NOT NULL REFERENCES order_data.orders(id),
    product_id   UUID          NOT NULL,
    variant_id   UUID,
    product_name VARCHAR(500)  NOT NULL,
    variant_label VARCHAR(200),
    sku          VARCHAR(100),
    unit_price   NUMERIC(12,2) NOT NULL,
    quantity     INT           NOT NULL,
    line_total   NUMERIC(12,2) NOT NULL,
    image_url    VARCHAR(2048),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by   UUID
);

CREATE INDEX idx_oi_order   ON order_data.order_items (order_id);
CREATE INDEX idx_oi_product ON order_data.order_items (product_id);

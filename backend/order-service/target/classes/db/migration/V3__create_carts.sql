CREATE TABLE order_data.carts (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL,
    customer_id UUID,
    session_id  VARCHAR(255),
    coupon_code VARCHAR(50),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

CREATE INDEX idx_cart_customer ON order_data.carts (tenant_id, customer_id)
    WHERE status = 'ACTIVE' AND customer_id IS NOT NULL;
CREATE INDEX idx_cart_session  ON order_data.carts (session_id)
    WHERE session_id IS NOT NULL;

CREATE TABLE order_data.cart_items (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID          NOT NULL,
    cart_id      UUID          NOT NULL REFERENCES order_data.carts(id),
    product_id   UUID          NOT NULL,
    variant_id   UUID,
    product_name VARCHAR(500)  NOT NULL,
    image_url    VARCHAR(2048),
    unit_price   NUMERIC(12,2) NOT NULL,
    quantity     INT           NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    CONSTRAINT uq_cart_item UNIQUE (cart_id, product_id, variant_id),
    CONSTRAINT chk_cart_qty CHECK (quantity > 0)
);

CREATE INDEX idx_ci_cart ON order_data.cart_items (cart_id);

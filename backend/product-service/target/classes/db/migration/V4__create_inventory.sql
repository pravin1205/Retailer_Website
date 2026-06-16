CREATE TABLE product.inventory (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    product_id          UUID        NOT NULL REFERENCES product.products(id),
    warehouse_code      VARCHAR(100) NOT NULL DEFAULT 'DEFAULT',
    quantity_on_hand    INT         NOT NULL DEFAULT 0,
    quantity_reserved   INT         NOT NULL DEFAULT 0,
    low_stock_threshold INT         NOT NULL DEFAULT 5,
    reorder_point       INT         NOT NULL DEFAULT 10,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_inventory UNIQUE (tenant_id, product_id, warehouse_code),
    CONSTRAINT chk_qty_non_negative CHECK (quantity_on_hand >= 0)
);

CREATE INDEX idx_inv_tenant_product ON product.inventory (tenant_id, product_id);
CREATE INDEX idx_inv_low_stock      ON product.inventory (tenant_id, quantity_on_hand)
    WHERE quantity_on_hand <= low_stock_threshold;

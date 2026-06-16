CREATE TABLE product.product_variants (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID          NOT NULL,
    product_id  UUID          NOT NULL REFERENCES product.products(id),
    label       VARCHAR(200)  NOT NULL,
    sku         VARCHAR(100),
    barcode     VARCHAR(100),
    price_delta NUMERIC(12,2) NOT NULL DEFAULT 0,
    attributes  JSONB         NOT NULL DEFAULT '{}',
    is_active   BOOLEAN       NOT NULL DEFAULT true,
    sort_order  INT           NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_pv_product ON product.product_variants (product_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_pv_tenant  ON product.product_variants (tenant_id)  WHERE deleted_at IS NULL;

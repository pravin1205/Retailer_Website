CREATE TABLE product.products (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID          NOT NULL,
    category_id UUID          REFERENCES product.categories(id),
    name        VARCHAR(500)  NOT NULL,
    brand       VARCHAR(200),
    description TEXT,
    sku         VARCHAR(100),
    barcode     VARCHAR(100),
    unit        VARCHAR(50),
    price       NUMERIC(12,2) NOT NULL,
    mrp         NUMERIC(12,2),
    cost_price  NUMERIC(12,2),
    tax_rate    NUMERIC(5,2)  NOT NULL DEFAULT 0,
    is_active   BOOLEAN       NOT NULL DEFAULT true,
    is_featured BOOLEAN       NOT NULL DEFAULT false,
    tags        TEXT[],
    attributes  JSONB         NOT NULL DEFAULT '{}',
    images      JSONB         NOT NULL DEFAULT '[]',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_product_tenant_sku UNIQUE (tenant_id, sku)
);

CREATE INDEX idx_prod_tenant   ON product.products (tenant_id)                         WHERE deleted_at IS NULL;
CREATE INDEX idx_prod_category ON product.products (tenant_id, category_id)            WHERE deleted_at IS NULL;
CREATE INDEX idx_prod_sku      ON product.products (tenant_id, sku)                    WHERE sku IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_prod_tags     ON product.products USING gin(tags);
CREATE INDEX idx_prod_attrs    ON product.products USING gin(attributes);

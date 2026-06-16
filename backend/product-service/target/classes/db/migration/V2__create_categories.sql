CREATE TABLE product.categories (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    parent_id   UUID         REFERENCES product.categories(id),
    name        VARCHAR(200) NOT NULL,
    slug        VARCHAR(200) NOT NULL,
    description TEXT,
    image_url   VARCHAR(2048),
    sort_order  INT          NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT uq_category_tenant_slug UNIQUE (tenant_id, slug)
);

CREATE INDEX idx_cat_tenant ON product.categories (tenant_id)           WHERE deleted_at IS NULL;
CREATE INDEX idx_cat_parent ON product.categories (parent_id)           WHERE deleted_at IS NULL;

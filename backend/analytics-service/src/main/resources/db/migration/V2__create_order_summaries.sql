CREATE TABLE analytics.order_summaries (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID          NOT NULL,
    summary_date      DATE          NOT NULL,
    total_orders      INT           NOT NULL DEFAULT 0,
    completed_orders  INT           NOT NULL DEFAULT 0,
    cancelled_orders  INT           NOT NULL DEFAULT 0,
    gross_revenue     NUMERIC(14,2) NOT NULL DEFAULT 0,
    net_revenue       NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_discount    NUMERIC(14,2) NOT NULL DEFAULT 0,
    new_customers     INT           NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_order_summary UNIQUE (tenant_id, summary_date)
);

CREATE INDEX idx_os_tenant_date ON analytics.order_summaries
    (tenant_id, summary_date DESC);

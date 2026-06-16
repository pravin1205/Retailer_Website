CREATE TABLE order_data.payments (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL,
    order_id            UUID          NOT NULL REFERENCES order_data.orders(id),
    amount              NUMERIC(12,2) NOT NULL,
    currency            CHAR(3)       NOT NULL DEFAULT 'INR',
    method              VARCHAR(30)   NOT NULL,
    gateway             VARCHAR(50),
    gateway_payment_id  VARCHAR(255),
    gateway_order_id    VARCHAR(255),
    status              VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    paid_at             TIMESTAMPTZ,
    refunded_at         TIMESTAMPTZ,
    refund_amount       NUMERIC(12,2),
    failure_reason      VARCHAR(255),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_pay_order   ON order_data.payments (order_id);
CREATE INDEX idx_pay_gateway ON order_data.payments (gateway_payment_id)
    WHERE gateway_payment_id IS NOT NULL;

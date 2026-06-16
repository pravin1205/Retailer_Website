CREATE TABLE notification.notifications (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID,
    recipient_id     UUID         NOT NULL,
    recipient_email  VARCHAR(320),
    recipient_phone  VARCHAR(20),
    channel          VARCHAR(20)  NOT NULL,    -- EMAIL | SMS | PUSH | IN_APP
    category         VARCHAR(50)  NOT NULL,    -- ORDER_UPDATE | LOW_STOCK | WELCOME | etc.
    title            VARCHAR(255) NOT NULL,
    body             TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    sent_at          TIMESTAMPTZ,
    read_at          TIMESTAMPTZ,
    reference_id     UUID,
    reference_type   VARCHAR(50),
    failure_reason   VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ
);

CREATE INDEX idx_notif_recipient ON notification.notifications
    (recipient_id, created_at DESC);
CREATE INDEX idx_notif_tenant    ON notification.notifications
    (tenant_id, created_at DESC) WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_notif_unread    ON notification.notifications
    (recipient_id, status, channel) WHERE status = 'SENT' AND channel = 'IN_APP';

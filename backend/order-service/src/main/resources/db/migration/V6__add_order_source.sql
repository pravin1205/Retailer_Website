-- ============================================================
-- V6 — Add order source channel tracking
--
-- Tracks which channel placed the order so the seller dashboard
-- can show a channel badge (App vs WhatsApp) and analytics can
-- segment revenue by acquisition channel.
--
-- Values: APP (default, existing orders) | WHATSAPP
-- ============================================================

ALTER TABLE order_data.orders
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'APP';

-- Index to support channel-based filtering in the seller dashboard
-- and analytics queries (e.g. "show me all WhatsApp orders today")
CREATE INDEX idx_ord_source ON order_data.orders (tenant_id, source);

-- Comment for documentation
COMMENT ON COLUMN order_data.orders.source IS
    'Order channel: APP = placed via web/mobile UI, WHATSAPP = placed via WhatsApp conversation';

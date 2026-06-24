package com.marketly.whatsapp.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Represents one customer's active conversation with one seller's WhatsApp store.
 *
 * Stored in Redis under the key:
 *   wa:conv:{phoneNumberId}:{customerWaId}
 *
 * TTL: configured via app.conversation.session-ttl-minutes (default 30 min).
 * TTL is refreshed on every message so active conversations never expire mid-flow.
 *
 * One session exists per (phoneNumberId, customerWaId) pair — meaning a customer
 * who messages two different stores has two independent sessions.
 *
 * This class is intentionally kept flat (no nested objects) to make Redis
 * serialisation/deserialisation simple and reliable across restarts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSession implements Serializable {

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Meta's internal identifier for the seller's WhatsApp phone number.
     * Used to route replies FROM the correct seller number.
     */
    private String phoneNumberId;

    /** The customer's WhatsApp number in E.164 format (e.g. "919876543210"). */
    private String customerWaId;

    // ── Tenant context ────────────────────────────────────────────────────────

    /** UUID of the tenant (seller store) this conversation belongs to. */
    private UUID tenantId;

    /** Human-readable slug of the tenant (e.g. "fresh-bakery"). */
    private String tenantSlug;

    // ── Customer context ──────────────────────────────────────────────────────

    /**
     * UUID of the platform customer account linked to this WhatsApp number.
     * Null until OTP verification is completed.
     */
    private UUID customerId;

    /**
     * JWT access token for the authenticated customer.
     * Used to call platform APIs (product-service, order-service, etc.) on
     * behalf of this customer without re-authenticating on every message.
     * Refreshed automatically when it expires.
     */
    private String accessToken;

    /** Customer's first name — personalises greeting messages. */
    private String customerName;

    // ── Conversation state ────────────────────────────────────────────────────

    /**
     * Current state of the conversation.
     * Drives routing logic in MessageRouter.
     */
    @Builder.Default
    private ConversationState state = ConversationState.UNVERIFIED;

    /**
     * Detected language of the conversation ("en", "hi", "ta", "te", "kn", …).
     * Responses are sent in this language. Set on first message, updated if the
     * customer switches language.
     */
    @Builder.Default
    private String language = "en";

    // ── Order-in-progress ─────────────────────────────────────────────────────

    /**
     * UUID of the cart currently being built.
     * Created when the customer adds their first item; cleared after checkout.
     */
    private UUID cartId;

    /**
     * UUID of the most recently placed order.
     * Retained for "track my order" and post-order issue reporting.
     */
    private UUID lastOrderId;

    /**
     * Order number of the last placed order (e.g. "MKT-20260623-0042").
     * Included in status update messages to help the customer identify the order.
     */
    private String lastOrderNumber;

    /**
     * ID of the delivery address selected by the customer.
     * Format: a UUID string (for a saved address) or "MANUAL:{full address text}"
     * (when the customer typed their address directly in the chat).
     * Set when session transitions from ADDRESS_SELECT to SLOT_SELECT.
     */
    private String selectedAddressId;

    /**
     * Delivery slot selected by the customer (e.g. "MORNING", "EVENING").
     * Set when session transitions from SLOT_SELECT to AWAITING_PAYMENT.
     */
    private String selectedDeliverySlot;

    /**
     * Serialised JSON of the pending order summary shown to the customer
     * before they confirm payment.
     * Retained so we can re-send it without re-calculating if the customer
     * asks for a repeat.
     */
    private String pendingOrderSummaryJson;

    // ── Timestamps ────────────────────────────────────────────────────────────

    /** Unix epoch millis of the last message received from the customer. */
    private long lastMessageAt;
}

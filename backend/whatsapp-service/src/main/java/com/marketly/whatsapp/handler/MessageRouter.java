package com.marketly.whatsapp.handler;

import com.marketly.whatsapp.meta.MetaApiClient;
import com.marketly.whatsapp.service.TenantResolver;
import com.marketly.whatsapp.session.ConversationSession;
import com.marketly.whatsapp.session.ConversationState;
import com.marketly.whatsapp.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Central routing hub for all inbound WhatsApp messages.
 *
 * Called by WebhookController for every inbound message. Runs on a dedicated
 * async executor so the webhook endpoint can return HTTP 200 immediately.
 *
 * Routing logic (evaluated top-to-bottom):
 *  1. Global commands (STATUS, CANCEL, HELP, MENU, ORDERS, STOP) are handled
 *     regardless of the current conversation state.
 *  2. UNVERIFIED / AWAITING_OTP → AuthHandler (OTP account linking).
 *  3. Audio messages + COLLECTING_ITEMS/IDLE → OrderCollectionHandler (STT + NLP).
 *  4. State-specific routing:
 *       IDLE / COLLECTING_ITEMS → OrderCollectionHandler
 *       ADDRESS_SELECT          → AddressHandler
 *       SLOT_SELECT             → SlotHandler
 *       AWAITING_PAYMENT        → PaymentHandler
 *  5. Fallback → help nudge.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageRouter {

    /** Global commands that bypass the current conversation state. */
    private static final Set<String> GLOBAL_COMMANDS = Set.of(
        "STATUS", "ORDERS", "CANCEL", "HELP", "MENU", "STOP"
    );

    private final SessionManager          sessionManager;
    private final TenantResolver          tenantResolver;
    private final MetaApiClient           metaApiClient;
    private final AuthHandler             authHandler;
    private final OrderCollectionHandler  orderCollectionHandler;
    private final AddressHandler          addressHandler;
    private final SlotHandler             slotHandler;
    private final PaymentHandler          paymentHandler;

    // ── Async dispatch ────────────────────────────────────────────────────────

    /**
     * Routes an inbound message to the appropriate handler.
     * Runs asynchronously — the webhook controller returns 200 immediately.
     *
     * @param phoneNumberId Meta's internal seller phone number ID.
     * @param customerWaId  Customer's WhatsApp number in E.164 format.
     * @param customerName  Display name from contacts block (may be null).
     * @param message       Raw message object from Meta webhook payload.
     */
    @Async
    public void route(String phoneNumberId, String customerWaId,
                      String customerName, Map<String, Object> message) {
        try {
            ConversationSession session = sessionManager.getOrCreate(phoneNumberId, customerWaId);

            // Enrich session with contact name on first message
            if (customerName != null && session.getCustomerName() == null) {
                session.setCustomerName(customerName);
                sessionManager.save(session);
            }

            // Resolve tenantId if not yet set in the session
            if (session.getTenantId() == null) {
                session.setTenantId(tenantResolver.resolve(phoneNumberId));
                sessionManager.save(session);
            }

            String messageType = (String) message.get("type");
            log.info("Routing message type={} state={} tenant={} customer={}",
                     messageType, session.getState(), session.getTenantId(), customerWaId);

            // ── 1. Global commands (text messages only) ───────────────────────
            if ("text".equals(messageType)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> textObj = (Map<String, Object>) message.get("text");
                String body = textObj != null
                    ? ((String) textObj.get("body")).trim().toUpperCase() : "";

                if (GLOBAL_COMMANDS.contains(body)) {
                    handleGlobalCommand(session, body);
                    return;
                }
            }

            // ── 2. Auth states ────────────────────────────────────────────────
            ConversationState state = session.getState();
            if (state == ConversationState.UNVERIFIED
                    || state == ConversationState.AWAITING_OTP) {
                authHandler.handle(session, message);
                return;
            }

            // ── 3. Audio messages — always go to order collection (STT + NLP) ─
            if ("audio".equals(messageType)) {
                orderCollectionHandler.handle(session, message);
                return;
            }

            // ── 4. State-specific routing ─────────────────────────────────────
            switch (state) {
                case IDLE, COLLECTING_ITEMS -> orderCollectionHandler.handle(session, message);
                case ADDRESS_SELECT         -> addressHandler.handle(session, message);
                case SLOT_SELECT            -> slotHandler.handle(session, message);
                case AWAITING_PAYMENT       -> paymentHandler.handle(session, message);
                case ORDER_PLACED           -> {
                    // Session should have transitioned to IDLE by now, but handle gracefully
                    session.setState(ConversationState.IDLE);
                    sessionManager.save(session);
                    orderCollectionHandler.handle(session, message);
                }
                default -> handleFallback(session);
            }

        } catch (TenantResolver.TenantNotFoundException e) {
            log.warn("MessageRouter: no tenant for phoneNumberId={} — ignoring", phoneNumberId);
        } catch (Exception e) {
            log.error("MessageRouter: unhandled error for phoneNumberId={} customer={}: {}",
                      phoneNumberId, customerWaId, e.getMessage(), e);
            safelySendError(phoneNumberId, customerWaId);
        }
    }

    // ── Global command handling ───────────────────────────────────────────────

    private void handleGlobalCommand(ConversationSession session, String command) {
        String phoneNumberId = session.getPhoneNumberId();
        String customerWaId  = session.getCustomerWaId();
        String accessToken   = resolveAccessToken(session);

        switch (command) {
            case "HELP" -> metaApiClient.sendText(phoneNumberId, accessToken, customerWaId,
                buildHelpMessage(session));

            case "STOP" -> {
                metaApiClient.sendText(phoneNumberId, accessToken, customerWaId,
                    "You have unsubscribed from WhatsApp messages from this store.\n" +
                    "Reply START anytime to re-subscribe.");
                sessionManager.delete(phoneNumberId, customerWaId);
            }

            case "CANCEL" -> {
                session.setState(ConversationState.IDLE);
                session.setCartId(null);
                session.setPendingOrderSummaryJson(null);
                session.setSelectedAddressId(null);
                session.setSelectedDeliverySlot(null);
                sessionManager.save(session);
                metaApiClient.sendText(phoneNumberId, accessToken, customerWaId,
                    "Your current order has been cleared. ✅\n" +
                    "Tell me what you'd like to order!");
            }

            case "STATUS" -> {
                String msg = session.getLastOrderNumber() != null
                    ? "Your last order: *" + session.getLastOrderNumber() + "*\n" +
                      "Reply ORDERS to see all recent orders."
                    : "You haven't placed any orders yet. Tell me what you'd like to order!";
                metaApiClient.sendText(phoneNumberId, accessToken, customerWaId, msg);
            }

            case "ORDERS" -> {
                String msg = session.getLastOrderNumber() != null
                    ? "Your most recent order: *" + session.getLastOrderNumber() + "*"
                    : "No recent orders found. Start ordering by telling me what you'd like!";
                metaApiClient.sendText(phoneNumberId, accessToken, customerWaId, msg);
            }

            case "MENU" -> metaApiClient.sendText(phoneNumberId, accessToken, customerWaId,
                "🛒 Just tell me what you'd like to order!\n\n" +
                "Examples:\n" +
                "• \"2 litres of milk\"\n" +
                "• \"1 dozen eggs and a loaf of bread\"\n" +
                "• \"Amul butter 500g\"\n\n" +
                "You can also send a voice note in Hindi, Tamil, Telugu, Kannada, or English! 🎙");

            default -> log.warn("Unhandled global command: {}", command);
        }
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    private void handleFallback(ConversationSession session) {
        metaApiClient.sendText(
            session.getPhoneNumberId(), resolveAccessToken(session), session.getCustomerWaId(),
            "I didn't quite understand that. 🤔\n\n" +
            "Try typing your order (e.g. \"2 milk 1 bread\") or reply HELP to see what I can do."
        );
    }

    private void safelySendError(String phoneNumberId, String customerWaId) {
        log.error("Could not send error message to customer={} on phoneNumberId={} " +
                  "— no access token available", customerWaId, phoneNumberId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildHelpMessage(ConversationSession session) {
        String name = session.getCustomerName() != null
            ? "Hi " + session.getCustomerName() + "! " : "";
        return name + "Here's what you can do:\n\n" +
               "🛒 *Order* — Type what you want (e.g. \"2 milk 1 bread\")\n" +
               "🎙 *Voice* — Send a voice note in Hindi, Tamil, Telugu, Kannada, or English\n" +
               "📦 *STATUS* — Check your last order\n" +
               "📋 *ORDERS* — See recent orders\n" +
               "🗂 *MENU* — Browse what we sell\n" +
               "❌ *CANCEL* — Cancel current order in progress\n" +
               "🚫 *STOP* — Unsubscribe from messages";
    }

    private String resolveAccessToken(ConversationSession session) {
        return session.getAccessToken() != null ? session.getAccessToken() : "";
    }
}

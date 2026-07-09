package com.marketly.whatsapp.handler;

import com.marketly.whatsapp.meta.MetaApiClient;
import com.marketly.whatsapp.session.ConversationSession;
import com.marketly.whatsapp.session.ConversationState;
import com.marketly.whatsapp.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Handles the SLOT_SELECT conversation state.
 *
 * Presents the seller's configured delivery slots as WhatsApp interactive
 * buttons and records the customer's selection.
 *
 * Slot definitions are hardcoded here as defaults. In a future iteration,
 * these will be fetched from tenant-settings where the seller can configure
 * their own slot names and time windows.
 *
 * After slot selection, transitions to AWAITING_PAYMENT and hands off to
 * PaymentHandler to show the order summary and collect payment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlotHandler {

    private final SessionManager sessionManager;
    private final MetaApiClient  metaApiClient;
    private final PaymentHandler paymentHandler;

    // Default delivery slots — seller-configurable in a future iteration
    private static final List<SlotOption> DEFAULT_SLOTS = List.of(
        new SlotOption("MORNING",  "Morning",  "9 AM – 12 PM"),
        new SlotOption("AFTERNOON","Afternoon","12 PM – 5 PM"),
        new SlotOption("EVENING",  "Evening",  "5 PM – 9 PM")
    );

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Called by MessageRouter when session state is SLOT_SELECT.
     *
     * @param session The current conversation session.
     * @param message Raw inbound message.
     */
    public void handle(ConversationSession session, Map<String, Object> message) {
        String messageType = (String) message.get("type");

        if ("interactive".equals(messageType)) {
            handleSlotSelection(session, message);
            return;
        }

        if ("text".equals(messageType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> textObj = (Map<String, Object>) message.get("text");
            String body = textObj != null ? ((String) textObj.get("body")).trim().toUpperCase() : "";
            // Allow text shortcuts: "MORNING", "EVENING", "1", "2", "3"
            if ("MORNING".equals(body) || "1".equals(body)) {
                confirmSlot(session, DEFAULT_SLOTS.get(0));
                return;
            }
            if ("AFTERNOON".equals(body) || "2".equals(body)) {
                confirmSlot(session, DEFAULT_SLOTS.get(1));
                return;
            }
            if ("EVENING".equals(body) || "3".equals(body)) {
                confirmSlot(session, DEFAULT_SLOTS.get(2));
                return;
            }
        }

        // First entry into this state — present slot buttons
        presentSlots(session);
    }

    /**
     * Called directly by AddressHandler immediately after address selection
     * so the customer sees slot options without sending another message.
     */
    public void presentSlots(ConversationSession session) {
        metaApiClient.sendButtons(
            session.getPhoneNumberId(), session.getAccessToken(), session.getCustomerWaId(),
            "When would you like your order delivered?",
            List.of("Morning 9–12", "Afternoon 12–5", "Evening 5–9")
        );
    }

    // ── Slot selection ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleSlotSelection(ConversationSession session, Map<String, Object> message) {
        Map<String, Object> interactive = (Map<String, Object>) message.get("interactive");
        if (interactive == null) { presentSlots(session); return; }

        Map<String, Object> buttonReply = (Map<String, Object>) interactive.get("button_reply");
        if (buttonReply == null) { presentSlots(session); return; }

        String replyId = ((String) buttonReply.getOrDefault("id", "")).toUpperCase();

        SlotOption chosen = DEFAULT_SLOTS.stream()
            .filter(s -> replyId.contains(s.id()))
            .findFirst()
            .orElse(null);

        if (chosen == null) { presentSlots(session); return; }
        confirmSlot(session, chosen);
    }

    private void confirmSlot(ConversationSession session, SlotOption slot) {
        session.setSelectedDeliverySlot(slot.id());
        session.setState(ConversationState.AWAITING_PAYMENT);
        sessionManager.save(session);

        log.info("Slot selected: {} for tenant={}", slot.id(), session.getTenantId());

        // Hand off to PaymentHandler to show order summary
        paymentHandler.presentOrderSummary(session);
    }

    // ── Value type ────────────────────────────────────────────────────────────

    private record SlotOption(String id, String name, String window) {}
}

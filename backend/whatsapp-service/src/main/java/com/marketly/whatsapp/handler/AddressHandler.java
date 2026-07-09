package com.marketly.whatsapp.handler;

import com.marketly.whatsapp.meta.MetaApiClient;
import com.marketly.whatsapp.session.ConversationSession;
import com.marketly.whatsapp.session.ConversationState;
import com.marketly.whatsapp.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles the ADDRESS_SELECT conversation state.
 *
 * Flow:
 *  1. When session enters ADDRESS_SELECT, fetch the customer's saved addresses
 *     from customer-service.
 *  2. Present addresses as a WhatsApp interactive list message.
 *  3. Customer selects an address (or types a new one).
 *  4. Selected address ID is stored in the session.
 *  5. Immediately present slot buttons via SlotHandler (no extra message needed).
 *  6. Session transitions to SLOT_SELECT.
 *
 * If the customer has no saved addresses, prompt them to type one.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AddressHandler {

    private final SessionManager    sessionManager;
    private final MetaApiClient     metaApiClient;
    private final SlotHandler       slotHandler;
    private final WebClient.Builder webClientBuilder;

    @Value("${app.services.customer-service-url:http://localhost:8084}")
    private String customerServiceUrl;

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Called by MessageRouter when session state is ADDRESS_SELECT.
     * If this is the first call in this state, presents the address list.
     * If the customer has replied with a selection, processes the choice.
     *
     * @param session The current conversation session.
     * @param message Raw inbound message (may be an interactive reply or text).
     */
    public void handle(ConversationSession session, Map<String, Object> message) {
        String messageType = (String) message.get("type");

        // Interactive list/button reply — customer selected an address
        if ("interactive".equals(messageType)) {
            handleAddressSelection(session, message);
            return;
        }

        // Text reply — could be a typed address
        if ("text".equals(messageType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> textObj = (Map<String, Object>) message.get("text");
            String body = textObj != null ? ((String) textObj.get("body")).trim() : null;
            if (body != null && !body.isBlank()) {
                handleTextAddressInput(session, body);
                return;
            }
        }

        // First entry into this state — show the address list
        presentAddresses(session);
    }

    /**
     * Called directly by OrderCollectionHandler when the customer taps "Checkout",
     * to immediately show the address list without waiting for another message.
     */
    public void presentAddresses(ConversationSession session) {
        List<Map<String, Object>> addresses = fetchAddresses(session);

        if (addresses.isEmpty()) {
            metaApiClient.sendText(
                session.getPhoneNumberId(), session.getAccessToken(), session.getCustomerWaId(),
                "Please type your delivery address (e.g. \"12 MG Road, Koramangala, Bangalore 560034\"):"
            );
            return;
        }

        // Build WhatsApp list rows — one per address
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < addresses.size(); i++) {
            Map<String, Object> addr = addresses.get(i);
            String id    = (String) addr.get("id");
            String label = (String) addr.getOrDefault("label", "Address " + (i + 1));
            String line1 = (String) addr.getOrDefault("line1", "");
            String city  = (String) addr.getOrDefault("city", "");
            rows.add(Map.of(
                "id",          "ADDR_" + id,
                "title",       label,
                "description", line1 + (city.isBlank() ? "" : ", " + city)
            ));
        }

        List<Map<String, Object>> sections = List.of(
            Map.of("title", "Saved addresses", "rows", rows)
        );

        metaApiClient.sendList(
            session.getPhoneNumberId(), session.getAccessToken(), session.getCustomerWaId(),
            "Where should we deliver your order?",
            "Choose address",
            sections
        );
    }

    // ── Address selection ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleAddressSelection(ConversationSession session, Map<String, Object> message) {
        Map<String, Object> interactive = (Map<String, Object>) message.get("interactive");
        if (interactive == null) { presentAddresses(session); return; }

        Map<String, Object> listReply = (Map<String, Object>) interactive.get("list_reply");
        if (listReply == null) { presentAddresses(session); return; }

        String replyId = (String) listReply.get("id");
        if (replyId == null || !replyId.startsWith("ADDR_")) {
            presentAddresses(session);
            return;
        }

        String addressId = replyId.substring(5); // strip "ADDR_" prefix
        session.setSelectedAddressId(addressId);
        session.setState(ConversationState.SLOT_SELECT);
        sessionManager.save(session);

        log.info("Address selected: {} for session tenant={}", addressId, session.getTenantId());

        // Present slot buttons immediately — customer should not need to send
        // another message just to see available delivery slots.
        slotHandler.presentSlots(session);
    }

    private void handleTextAddressInput(ConversationSession session, String addressText) {
        // Store the typed address text as a special "manual" address ID signal.
        // The PaymentHandler will include this in the checkout request as a new address.
        session.setSelectedAddressId("MANUAL:" + addressText);
        session.setState(ConversationState.SLOT_SELECT);
        sessionManager.save(session);
        // Present slot buttons immediately.
        slotHandler.presentSlots(session);
    }

    // ── Platform API ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchAddresses(ConversationSession session) {
        try {
            Map<String, Object> response = (Map<String, Object>) webClientBuilder
                .baseUrl(customerServiceUrl)
                .defaultHeader("X-Tenant-ID",  session.getTenantId().toString())
                .defaultHeader("X-User-ID",     session.getCustomerId().toString())
                .defaultHeader("Authorization", "Bearer " + session.getAccessToken())
                .build()
                .get()
                .uri("/api/v1/customers/me/addresses")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) return List.of();
            Object data = response.get("data");
            return data instanceof List ? (List<Map<String, Object>>) data : List.of();

        } catch (Exception e) {
            log.error("Failed to fetch addresses for tenant={}: {}", session.getTenantId(), e.getMessage());
            return List.of();
        }
    }
}

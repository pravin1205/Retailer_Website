package com.marketly.whatsapp.handler;

import com.marketly.whatsapp.meta.MetaApiClient;
import com.marketly.whatsapp.nlp.NlpService;
import com.marketly.whatsapp.nlp.NlpService.ExtractionResult;
import com.marketly.whatsapp.nlp.NlpService.ExtractedItem;
import com.marketly.whatsapp.session.ConversationSession;
import com.marketly.whatsapp.session.ConversationState;
import com.marketly.whatsapp.session.SessionManager;
import com.marketly.whatsapp.stt.SpeechToTextService;
import com.marketly.whatsapp.stt.SpeechToTextService.TranscriptionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles the COLLECTING_ITEMS conversation state.
 *
 * This is the heart of the WhatsApp ordering experience. It:
 *  1. Accepts text or voice messages from the customer describing items.
 *  2. Runs STT on voice notes (via SpeechToTextService).
 *  3. Extracts order intent via NLP (via NlpService).
 *  4. Resolves product names to actual products via product-service search.
 *  5. Adds matched products to the customer's cart via order-service.
 *  6. Handles "same as last time" reorder shortcut.
 *  7. Handles product disambiguation when multiple matches exist.
 *  8. Sends a running cart summary with "Add more" / "Checkout" buttons.
 *  9. On "Checkout" → transitions session to ADDRESS_SELECT.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCollectionHandler {

    private final SessionManager      sessionManager;
    private final MetaApiClient       metaApiClient;
    private final NlpService          nlpService;
    private final SpeechToTextService sttService;
    private final AddressHandler      addressHandler;
    private final WebClient.Builder   webClientBuilder;

    @Value("${app.services.product-service-url:http://localhost:8083}")
    private String productServiceUrl;

    @Value("${app.services.order-service-url:http://localhost:8085}")
    private String orderServiceUrl;

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Handles an inbound message in IDLE or COLLECTING_ITEMS state.
     * Called by MessageRouter for any text/audio message from a verified customer.
     *
     * @param session The current conversation session.
     * @param message Raw inbound message from Meta webhook.
     */
    public void handle(ConversationSession session, Map<String, Object> message) {
        String messageType = (String) message.get("type");

        // ── Voice note ────────────────────────────────────────────────────────
        if ("audio".equals(messageType)) {
            handleVoiceMessage(session, message);
            return;
        }

        // ── Text message ──────────────────────────────────────────────────────
        if ("text".equals(messageType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> textObj = (Map<String, Object>) message.get("text");
            String body = textObj != null ? (String) textObj.get("body") : null;

            if (body == null || body.isBlank()) {
                sendNudge(session);
                return;
            }

            // DONE keyword — customer wants to go to checkout
            if ("DONE".equalsIgnoreCase(body.trim())) {
                transitionToCheckout(session);
                return;
            }

            processTextOrder(session, body);
            return;
        }

        // ── Interactive button reply (e.g. "Add more" / "Checkout") ───────────
        if ("interactive".equals(messageType)) {
            handleInteractiveReply(session, message);
            return;
        }

        sendNudge(session);
    }

    // ── Voice handling ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleVoiceMessage(ConversationSession session, Map<String, Object> message) {
        Map<String, Object> audio = (Map<String, Object>) message.get("audio");
        if (audio == null) { sendNudge(session); return; }

        String mediaId = (String) audio.get("id");
        sendTypingIndicator(session);

        // Download audio from Meta
        byte[] audioBytes;
        try {
            audioBytes = downloadMedia(mediaId, session.getAccessToken());
        } catch (Exception e) {
            log.error("Failed to download voice note mediaId={}: {}", mediaId, e.getMessage());
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(),
                "I couldn't process your voice note. Please try typing your order instead.");
            return;
        }

        // Transcribe
        TranscriptionResult transcription = sttService.transcribe(audioBytes, "voice.ogg");
        if (transcription == null || transcription.text().isBlank()) {
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(),
                "I couldn't clearly understand your voice note. " +
                "Could you type your order instead? (e.g. \"2 milk 1 bread\")");
            return;
        }

        // Update session language from voice detection
        if (!transcription.language().equals(session.getLanguage())) {
            session.setLanguage(transcription.language());
        }

        log.info("Voice transcription: '{}' (lang={})", transcription.text(), transcription.language());
        processTextOrder(session, transcription.text());
    }

    // ── Text order processing ─────────────────────────────────────────────────

    private void processTextOrder(ConversationSession session, String text) {
        sendTypingIndicator(session);

        // NLP extraction
        ExtractionResult extraction = nlpService.extract(text);
        if (extraction == null) {
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(),
                "Sorry, I had trouble understanding that. " +
                "Try again — e.g. \"2 litres of milk and 1 loaf of bread\"");
            return;
        }

        // Handle "same as last time" reorder
        if (extraction.reorderIntent()) {
            handleReorderIntent(session);
            return;
        }

        // Handle clarification needed
        if (extraction.needsClarification()) {
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(), extraction.clarification());
            return;
        }

        // No items extracted — nudge customer
        if (!extraction.hasItems()) {
            sendNudge(session);
            return;
        }

        // Update session language from NLP detection
        if (!extraction.language().equals(session.getLanguage())) {
            session.setLanguage(extraction.language());
        }

        // Process each extracted item
        List<String> addedItems    = new ArrayList<>();
        List<String> notFoundItems = new ArrayList<>();

        for (ExtractedItem item : extraction.items()) {
            ProductMatch match = findProduct(session, item.name());
            if (match == null) {
                notFoundItems.add(item.name());
                continue;
            }

            int qty = (int) Math.max(1, Math.round(item.quantity()));
            boolean added = addToCart(session, match.productId(), qty);
            if (added) {
                addedItems.add(match.name() + " × " + qty
                    + (match.unit() != null ? " " + match.unit() : "")
                    + " = ₹" + (match.price() * qty));
            } else {
                notFoundItems.add(item.name());
            }
        }

        // Ensure session is in COLLECTING_ITEMS state
        if (session.getState() == ConversationState.IDLE) {
            session.setState(ConversationState.COLLECTING_ITEMS);
        }
        sessionManager.save(session);

        // Build response
        StringBuilder sb = new StringBuilder();
        if (!addedItems.isEmpty()) {
            sb.append("Got it! Added to your cart:\n");
            addedItems.forEach(i -> sb.append("• ").append(i).append("\n"));
        }
        if (!notFoundItems.isEmpty()) {
            sb.append("\n⚠️ Couldn't find: ");
            sb.append(String.join(", ", notFoundItems));
            sb.append("\n(Try a different name or browse MENU)");
        }

        if (addedItems.isEmpty()) {
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(),
                "I couldn't find any of those products. " +
                "Try MENU to see what's available, or describe the product differently.");
            return;
        }

        sb.append("\nAnything else to add?");

        metaApiClient.sendButtons(
            session.getPhoneNumberId(), session.getAccessToken(), session.getCustomerWaId(),
            sb.toString().trim(),
            List.of("Add more", "Checkout", "View cart")
        );
    }

    // ── Cart view ─────────────────────────────────────────────────────────────

    private void sendCartSummary(ConversationSession session) {
        if (session.getCartId() == null) {
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(), "Your cart is empty. Tell me what you'd like to order!");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> cart = (Map<String, Object>) fetchCart(session);
        if (cart == null) {
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(), "Couldn't load your cart. Please try again.");
            return;
        }

        metaApiClient.sendButtons(
            session.getPhoneNumberId(), session.getAccessToken(), session.getCustomerWaId(),
            buildCartText(cart),
            List.of("Checkout", "Add more", "Clear cart")
        );
    }

    // ── Interactive reply ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleInteractiveReply(ConversationSession session, Map<String, Object> message) {
        Map<String, Object> interactive = (Map<String, Object>) message.get("interactive");
        if (interactive == null) { sendNudge(session); return; }

        String type = (String) interactive.get("type");
        String replyId = null;

        if ("button_reply".equals(type)) {
            Map<String, Object> buttonReply = (Map<String, Object>) interactive.get("button_reply");
            replyId = buttonReply != null ? (String) buttonReply.get("id") : null;
        } else if ("list_reply".equals(type)) {
            Map<String, Object> listReply = (Map<String, Object>) interactive.get("list_reply");
            replyId = listReply != null ? (String) listReply.get("id") : null;
        }

        if (replyId == null) { sendNudge(session); return; }

        switch (replyId.toUpperCase()) {
            case "CHECKOUT" -> transitionToCheckout(session);
            case "ADD_MORE" -> metaApiClient.sendText(
                session.getPhoneNumberId(), session.getAccessToken(), session.getCustomerWaId(),
                "What else would you like to add?");
            case "VIEW_CART" -> sendCartSummary(session);
            case "CLEAR_CART" -> {
                clearCart(session);
                session.setState(ConversationState.IDLE);
                session.setCartId(null);
                sessionManager.save(session);
                metaApiClient.sendText(
                    session.getPhoneNumberId(), session.getAccessToken(), session.getCustomerWaId(),
                    "Cart cleared! Tell me what you'd like to order.");
            }
            default -> sendNudge(session);
        }
    }

    // ── Reorder intent ────────────────────────────────────────────────────────

    private void handleReorderIntent(ConversationSession session) {
        if (session.getLastOrderId() == null) {
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(),
                "You haven't placed any previous orders here yet. " +
                "Tell me what you'd like to order today!");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> lastOrder = (Map<String, Object>) fetchOrder(session, session.getLastOrderId().toString());
        if (lastOrder == null) {
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(), "Couldn't load your previous order. Please try again.");
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) lastOrder.get("items");
        String summary = buildPreviousOrderSummary(items);

        metaApiClient.sendButtons(
            session.getPhoneNumberId(), session.getAccessToken(), session.getCustomerWaId(),
            "Your last order was:\n" + summary + "\n\nShall I reorder the same?",
            List.of("Yes, reorder", "No, start fresh")
        );
    }

    // ── Checkout transition ───────────────────────────────────────────────────

    private void transitionToCheckout(ConversationSession session) {
        if (session.getCartId() == null) {
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(),
                "Your cart is empty. Tell me what you'd like to order first!");
            return;
        }
        session.setState(ConversationState.ADDRESS_SELECT);
        sessionManager.save(session);
        // Present address list immediately — customer should not need to send
        // another message just to see their address options.
        addressHandler.presentAddresses(session);
    }

    // ── Platform API calls ────────────────────────────────────────────────────

    /** Search for a product by name in the tenant's catalog. */
    @SuppressWarnings("unchecked")
    private ProductMatch findProduct(ConversationSession session, String productName) {
        try {
            Map<String, Object> response = (Map<String, Object>) webClientBuilder
                .baseUrl(productServiceUrl)
                .defaultHeader("X-Tenant-ID", session.getTenantId().toString())
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/products")
                    .queryParam("search", productName)
                    .queryParam("size", "5")
                    .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) return null;

            Map<String, Object> data    = (Map<String, Object>) response.get("data");
            List<Map<String, Object>> content = data != null
                ? (List<Map<String, Object>>) data.get("content") : null;
            if (content == null || content.isEmpty()) return null;

            // Take the first result — best relevance match
            Map<String, Object> product = content.get(0);
            Map<String, Object> inv     = (Map<String, Object>) product.getOrDefault("inventory", Map.of());
            int stock = inv.get("quantity") instanceof Number n ? n.intValue() : 0;
            if (stock <= 0) return null; // out of stock — skip

            return new ProductMatch(
                (String) product.get("id"),
                (String) product.get("name"),
                product.get("price") instanceof Number p ? p.doubleValue() : 0.0,
                (String) product.get("unit")
            );

        } catch (Exception e) {
            log.error("Product search failed for '{}': {}", productName, e.getMessage());
            return null;
        }
    }

    /** Adds a product to the customer's cart, creating it if needed. */
    @SuppressWarnings("unchecked")
    private boolean addToCart(ConversationSession session, String productId, int quantity) {
        try {
            Map<String, Object> itemPayload = Map.of(
                "productId", productId,
                "quantity",  quantity
            );

            if (session.getCartId() == null) {
                // Create a new cart first
                Map<String, Object> cartResponse = (Map<String, Object>) webClientBuilder
                    .baseUrl(orderServiceUrl)
                    .defaultHeader("X-Tenant-ID",  session.getTenantId().toString())
                    .defaultHeader("X-User-ID",     session.getCustomerId().toString())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + session.getAccessToken())
                    .build()
                    .post()
                    .uri("/api/v1/cart")
                    .bodyValue(Map.of("items", List.of(itemPayload)))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

                if (cartResponse == null || !Boolean.TRUE.equals(cartResponse.get("success"))) return false;
                Map<String, Object> cartData = (Map<String, Object>) cartResponse.get("data");
                session.setCartId(java.util.UUID.fromString((String) cartData.get("id")));
                sessionManager.save(session);
            } else {
                // Add item to existing cart
                webClientBuilder
                    .baseUrl(orderServiceUrl)
                    .defaultHeader("X-Tenant-ID",  session.getTenantId().toString())
                    .defaultHeader("X-User-ID",     session.getCustomerId().toString())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + session.getAccessToken())
                    .build()
                    .post()
                    .uri("/api/v1/cart/" + session.getCartId() + "/items")
                    .bodyValue(itemPayload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            }
            return true;

        } catch (Exception e) {
            log.error("Add to cart failed for productId={}: {}", productId, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Object fetchCart(ConversationSession session) {
        try {
            Map<String, Object> response = (Map<String, Object>) webClientBuilder
                .baseUrl(orderServiceUrl)
                .defaultHeader("X-Tenant-ID",  session.getTenantId().toString())
                .defaultHeader("X-User-ID",     session.getCustomerId().toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + session.getAccessToken())
                .build()
                .get()
                .uri("/api/v1/cart/" + session.getCartId())
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            return response != null ? response.get("data") : null;
        } catch (Exception e) {
            log.error("Fetch cart failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Object fetchOrder(ConversationSession session, String orderId) {
        try {
            Map<String, Object> response = (Map<String, Object>) webClientBuilder
                .baseUrl(orderServiceUrl)
                .defaultHeader("X-Tenant-ID",  session.getTenantId().toString())
                .defaultHeader("X-User-ID",     session.getCustomerId().toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + session.getAccessToken())
                .build()
                .get()
                .uri("/api/v1/orders/" + orderId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            return response != null ? response.get("data") : null;
        } catch (Exception e) {
            log.error("Fetch order failed: {}", e.getMessage());
            return null;
        }
    }

    private void clearCart(ConversationSession session) {
        // Cart clearing is handled by abandoning the cart ID.
        // A new cart will be created on the next add-to-cart call.
        // The old cart will be garbage-collected by the order-service cleanup job.
        session.setCartId(null);
        sessionManager.save(session);
    }

    private byte[] downloadMedia(String mediaId, String accessToken) {
        // Delegate to the injected MetaApiClient Spring bean
        return metaApiClient.downloadMedia(mediaId, accessToken);
    }

    // ── Message helpers ───────────────────────────────────────────────────────

    private void sendNudge(ConversationSession session) {
        metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
            session.getCustomerWaId(),
            "Just tell me what you'd like to order! " +
            "For example: \"2 litres of milk and a dozen eggs\" or send a voice note 🎙");
    }

    private void sendTypingIndicator(ConversationSession session) {
        // WhatsApp does not support typing indicators via Cloud API currently.
        // This is a no-op placeholder for future support.
    }

    @SuppressWarnings("unchecked")
    private String buildCartText(Map<String, Object> cart) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) cart.getOrDefault("items", List.of());
        StringBuilder sb = new StringBuilder("🛒 *Your cart:*\n");
        double total = 0;
        for (Map<String, Object> item : items) {
            String name  = (String) item.getOrDefault("productName", "Item");
            int qty      = item.get("quantity") instanceof Number n ? n.intValue() : 1;
            double price = item.get("lineTotal") instanceof Number p ? p.doubleValue() : 0;
            sb.append("• ").append(name).append(" × ").append(qty)
              .append(" = ₹").append(String.format("%.0f", price)).append("\n");
            total += price;
        }
        sb.append("\n*Total: ₹").append(String.format("%.0f", total)).append("*");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String buildPreviousOrderSummary(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) return "(no items)";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> item : items) {
            sb.append("• ").append(item.getOrDefault("name", "Item"))
              .append(" × ").append(item.getOrDefault("quantity", 1)).append("\n");
        }
        return sb.toString().trim();
    }

    // ── Value types ───────────────────────────────────────────────────────────

    private record ProductMatch(String productId, String name, double price, String unit) {}
}

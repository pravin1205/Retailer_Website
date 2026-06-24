package com.marketly.whatsapp.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketly.whatsapp.meta.MetaApiClient;
import com.marketly.whatsapp.session.ConversationSession;
import com.marketly.whatsapp.session.ConversationState;
import com.marketly.whatsapp.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/**
 * Handles the AWAITING_PAYMENT conversation state.
 *
 * Flow:
 *  1. presentOrderSummary() — called by SlotHandler after slot selection.
 *     Fetches the cart, calculates totals, shows a summary with PAY / COD buttons.
 *  2. Customer taps "Pay via UPI" → generateAndSendPaymentLink() creates a
 *     Razorpay payment link and sends it as a CTA button message.
 *  3. Customer taps "Cash on Delivery" → placeOrder() is called with COD.
 *  4. Razorpay fires a payment webhook to POST /whatsapp/payment/webhook.
 *     The PaymentWebhookController calls handlePaymentSuccess() here.
 *  5. On successful payment → placeOrder() is called with the gateway ref.
 *  6. Order placed → session transitions to ORDER_PLACED → sends confirmation.
 *
 * The checkout call is identical to what the web frontend makes:
 *   POST /api/v1/orders with CheckoutRequest { cartId, addressId, deliverySlot,
 *   paymentMethod, source: "WHATSAPP" }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentHandler {

    private final SessionManager    sessionManager;
    private final MetaApiClient     metaApiClient;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper      objectMapper;

    @Value("${app.services.order-service-url:http://localhost:8085}")
    private String orderServiceUrl;

    @Value("${app.razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${app.razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${app.razorpay.link-expiry-minutes:30}")
    private int linkExpiryMinutes;

    // ── Order summary ─────────────────────────────────────────────────────────

    /**
     * Shows the full order summary to the customer with PAY / COD options.
     * Called by SlotHandler after the delivery slot is confirmed.
     */
    @SuppressWarnings("unchecked")
    public void presentOrderSummary(ConversationSession session) {
        // Fetch cart to calculate totals
        Map<String, Object> cart = fetchCart(session);
        if (cart == null) {
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(),
                "Something went wrong loading your cart. Please try again or type CANCEL to start over.");
            return;
        }

        String summary = buildOrderSummary(cart, session);

        // Persist the summary so we can re-send without recalculating
        try {
            session.setPendingOrderSummaryJson(objectMapper.writeValueAsString(cart));
        } catch (Exception ignored) {}
        sessionManager.save(session);

        // If Razorpay is configured, offer UPI + COD. If not, COD only.
        if (razorpayKeyId != null && !razorpayKeyId.isBlank()) {
            metaApiClient.sendButtons(
                session.getPhoneNumberId(), session.getAccessToken(), session.getCustomerWaId(),
                summary,
                List.of("Pay via UPI", "Cash on Delivery", "Edit order")
            );
        } else {
            metaApiClient.sendButtons(
                session.getPhoneNumberId(), session.getAccessToken(), session.getCustomerWaId(),
                summary,
                List.of("Confirm COD order", "Edit order")
            );
        }
    }

    // ── Entry point for interactive replies in AWAITING_PAYMENT state ─────────

    /**
     * Called by MessageRouter when session is in AWAITING_PAYMENT and the
     * customer sends a message (typically a button tap).
     */
    @SuppressWarnings("unchecked")
    public void handle(ConversationSession session, Map<String, Object> message) {
        String messageType = (String) message.get("type");

        if ("interactive".equals(messageType)) {
            Map<String, Object> interactive = (Map<String, Object>) message.get("interactive");
            if (interactive == null) { presentOrderSummary(session); return; }

            Map<String, Object> buttonReply = (Map<String, Object>) interactive.get("button_reply");
            if (buttonReply == null) { presentOrderSummary(session); return; }

            String replyId = ((String) buttonReply.getOrDefault("id", "")).toUpperCase();

            if (replyId.contains("UPI") || replyId.contains("PAY")) {
                generateAndSendPaymentLink(session);
            } else if (replyId.contains("COD") || replyId.contains("CASH")) {
                placeOrder(session, "COD", null);
            } else if (replyId.contains("EDIT")) {
                session.setState(ConversationState.COLLECTING_ITEMS);
                sessionManager.save(session);
                metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                    session.getCustomerWaId(),
                    "No problem! Tell me what you'd like to change or add.");
            } else {
                presentOrderSummary(session);
            }
            return;
        }

        if ("text".equals(messageType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> textObj = (Map<String, Object>) message.get("text");
            String body = textObj != null ? ((String) textObj.get("body")).trim().toUpperCase() : "";
            if ("PAY".equals(body)) { generateAndSendPaymentLink(session); return; }
            if ("COD".equals(body))  { placeOrder(session, "COD", null); return; }
        }

        // Re-show summary if customer is confused
        presentOrderSummary(session);
    }

    // ── UPI payment link generation ───────────────────────────────────────────

    /**
     * Creates a Razorpay Payment Link and sends it in the WhatsApp chat.
     * The link expires after the configured number of minutes.
     */
    @SuppressWarnings("unchecked")
    private void generateAndSendPaymentLink(ConversationSession session) {
        Map<String, Object> cart = fetchCart(session);
        if (cart == null) { presentOrderSummary(session); return; }

        List<Map<String, Object>> items = (List<Map<String, Object>>) cart.getOrDefault("items", List.of());
        double totalAmount = items.stream()
            .mapToDouble(i -> i.get("lineTotal") instanceof Number n ? n.doubleValue() : 0.0)
            .sum();

        long amountPaise = Math.round(totalAmount * 100); // Razorpay uses paise

        try {
            String paymentLinkUrl = createRazorpayLink(session, amountPaise);

            metaApiClient.sendCtaUrl(
                session.getPhoneNumberId(), session.getAccessToken(), session.getCustomerWaId(),
                "Pay ₹" + String.format("%.0f", totalAmount) + " securely.\n" +
                "Link expires in " + linkExpiryMinutes + " minutes.",
                "Pay ₹" + String.format("%.0f", totalAmount),
                paymentLinkUrl
            );

            log.info("Razorpay payment link sent for tenant={} amount={}",
                     session.getTenantId(), amountPaise);

        } catch (Exception e) {
            log.error("Failed to create Razorpay link: {}", e.getMessage());
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(),
                "UPI payment is temporarily unavailable. Would you like to pay Cash on Delivery instead?\n\n" +
                "Reply COD to confirm.");
        }
    }

    /**
     * Calls the Razorpay Payment Links API to create a payment link.
     * Returns the short URL the customer taps to pay.
     */
    @SuppressWarnings("unchecked")
    private String createRazorpayLink(ConversationSession session, long amountPaise)
            throws Exception {

        long expiryEpoch = System.currentTimeMillis() / 1000 + (linkExpiryMinutes * 60L);

        Map<String, Object> payload = Map.of(
            "amount",      amountPaise,
            "currency",    "INR",
            "description", "Order from store " + session.getTenantSlug(),
            "customer", Map.of(
                "contact", "+" + session.getCustomerWaId()
            ),
            "expire_by",   expiryEpoch,
            "notify", Map.of("sms", false, "email", false), // notifications handled via WhatsApp
            // notes are forwarded to the payment webhook — used to route the
            // webhook event back to the correct conversation session.
            "notes", Map.of(
                "phoneNumberId", session.getPhoneNumberId(),
                "customerWaId",  session.getCustomerWaId(),
                "tenantId",      session.getTenantId().toString()
            )
        );

        // Basic auth: key_id:key_secret encoded as Base64
        String credentials = Base64.getEncoder().encodeToString(
            (razorpayKeyId + ":" + razorpayKeySecret).getBytes(StandardCharsets.UTF_8));

        Map<String, Object> response = (Map<String, Object>) webClientBuilder
            .baseUrl("https://api.razorpay.com")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
            .build()
            .post()
            .uri("/v1/payment_links")
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (response == null || response.get("short_url") == null) {
            throw new IllegalStateException("Razorpay did not return a payment link URL");
        }

        // Persist session so the Razorpay webhook handler can find it
        sessionManager.save(session);

        return (String) response.get("short_url");
    }

    // ── Razorpay payment webhook ──────────────────────────────────────────────

    /**
     * Called by PaymentWebhookController when Razorpay fires a
     * payment.link.paid event. This triggers the actual order placement.
     *
     * @param phoneNumberId   The seller's WhatsApp phone number ID.
     * @param customerWaId    The customer's WhatsApp number.
     * @param gatewayPaymentId Razorpay's payment_id for the transaction.
     */
    public void handlePaymentSuccess(String phoneNumberId, String customerWaId,
                                     String gatewayPaymentId) {
        sessionManager.find(phoneNumberId, customerWaId).ifPresentOrElse(
            session -> placeOrder(session, "UPI", gatewayPaymentId),
            () -> log.warn("Payment webhook received but no session found for " +
                           "phoneNumberId={} customer={}", phoneNumberId, customerWaId)
        );
    }

    // ── Order placement ───────────────────────────────────────────────────────

    /**
     * Places the order by calling the order-service checkout endpoint.
     * This is the same endpoint the web frontend calls — source is set to WHATSAPP.
     */
    @SuppressWarnings("unchecked")
    void placeOrder(ConversationSession session, String paymentMethod,
                    String gatewayPaymentId) {
        try {
            String addressId = session.getSelectedAddressId();
            boolean isManualAddress = addressId != null && addressId.startsWith("MANUAL:");

            Map<String, Object> checkoutPayload = buildCheckoutPayload(
                session, paymentMethod, isManualAddress, addressId);

            Map<String, Object> response = (Map<String, Object>) webClientBuilder
                .baseUrl(orderServiceUrl)
                .defaultHeader("X-Tenant-ID",  session.getTenantId().toString())
                .defaultHeader("X-User-ID",     session.getCustomerId().toString())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + session.getAccessToken())
                .build()
                .post()
                .uri("/api/v1/orders")
                .bodyValue(checkoutPayload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                throw new IllegalStateException("Order placement returned an error response");
            }

            Map<String, Object> order = (Map<String, Object>) response.get("data");
            String orderNumber = (String) order.get("orderNumber");
            String orderId     = (String) order.get("id");

            // Update session with placed order info
            session.setLastOrderId(UUID.fromString(orderId));
            session.setLastOrderNumber(orderNumber);
            session.setState(ConversationState.ORDER_PLACED);
            session.setCartId(null);
            session.setSelectedAddressId(null);
            session.setSelectedDeliverySlot(null);
            session.setPendingOrderSummaryJson(null);
            sessionManager.save(session);

            // Send confirmation
            sendOrderConfirmation(session, orderNumber, paymentMethod);

            // Transition back to IDLE so the customer can place another order
            session.setState(ConversationState.IDLE);
            sessionManager.save(session);

            log.info("WhatsApp order placed: {} for tenant={}", orderNumber, session.getTenantId());

        } catch (Exception e) {
            log.error("Order placement failed for tenant={}: {}", session.getTenantId(), e.getMessage(), e);
            metaApiClient.sendText(session.getPhoneNumberId(), session.getAccessToken(),
                session.getCustomerWaId(),
                "Sorry, we couldn't place your order right now. Please try again in a moment " +
                "or type CANCEL to start over.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildCheckoutPayload(ConversationSession session,
            String paymentMethod, boolean isManualAddress, String addressId) {

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("cartId",        session.getCartId().toString());
        payload.put("deliverySlot",  session.getSelectedDeliverySlot() != null
                                         ? session.getSelectedDeliverySlot() : "STANDARD");
        payload.put("paymentMethod", "COD".equals(paymentMethod) ? "COD" : "UPI");
        payload.put("source",        "WHATSAPP");

        if (isManualAddress) {
            // Customer typed the address — pass it as a snapshot map and as a note.
            // addressId format: "MANUAL:{full address text}"
            String addressText = addressId.substring(7);
            payload.put("notes",           "WhatsApp order — Deliver to: " + addressText);
            // addressSnapshot is embedded in the payload so CheckoutService can serialise it.
            // Use a sentinel UUID that tells order-service to use addressSnapshot instead.
            payload.put("addressId",       session.getCustomerId().toString());
            payload.put("addressSnapshot", Map.of(
                "line1", addressText,
                "city",  "",
                "state", "",
                "pincode", ""
            ));
        } else {
            payload.put("addressId", addressId);
            payload.put("notes",     "WhatsApp order");
        }

        return payload;
    }

    private void sendOrderConfirmation(ConversationSession session, String orderNumber,
                                       String paymentMethod) {
        String paymentLine = "COD".equals(paymentMethod)
            ? "💵 Payment: Cash on Delivery"
            : "✅ Payment: Received";

        String slot = session.getSelectedDeliverySlot() != null
            ? switch (session.getSelectedDeliverySlot()) {
                case "MORNING"   -> "Morning (9 AM – 12 PM)";
                case "AFTERNOON" -> "Afternoon (12 PM – 5 PM)";
                case "EVENING"   -> "Evening (5 PM – 9 PM)";
                default          -> session.getSelectedDeliverySlot();
              }
            : "As soon as possible";

        metaApiClient.sendText(
            session.getPhoneNumberId(), session.getAccessToken(), session.getCustomerWaId(),
            "✅ *Order Confirmed!*\n\n" +
            "Order: *" + orderNumber + "*\n" +
            "Delivery: " + slot + "\n" +
            paymentLine + "\n\n" +
            "We'll send you updates as your order is packed and dispatched.\n\n" +
            "Reply STATUS anytime to check your order. 📦"
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchCart(ConversationSession session) {
        if (session.getCartId() == null) return null;
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
            return response != null ? (Map<String, Object>) response.get("data") : null;
        } catch (Exception e) {
            log.error("PaymentHandler: cart fetch failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String buildOrderSummary(Map<String, Object> cart, ConversationSession session) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) cart.getOrDefault("items", List.of());
        StringBuilder sb = new StringBuilder("🛒 *Order Summary*\n\n");
        double subtotal = 0;
        for (Map<String, Object> item : items) {
            String name  = (String) item.getOrDefault("productName", "Item");
            int qty      = item.get("quantity") instanceof Number n ? n.intValue() : 1;
            double price = item.get("lineTotal") instanceof Number p ? p.doubleValue() : 0;
            sb.append("• ").append(name).append(" × ").append(qty)
              .append(" = ₹").append(String.format("%.0f", price)).append("\n");
            subtotal += price;
        }

        String slot = session.getSelectedDeliverySlot() != null
            ? switch (session.getSelectedDeliverySlot()) {
                case "MORNING"   -> "Morning (9 AM – 12 PM)";
                case "AFTERNOON" -> "Afternoon (12 PM – 5 PM)";
                case "EVENING"   -> "Evening (5 PM – 9 PM)";
                default          -> session.getSelectedDeliverySlot();
              }
            : "Standard";

        sb.append("\n*Total: ₹").append(String.format("%.0f", subtotal)).append("*\n");
        sb.append("📍 Delivery: ").append(slot).append("\n\n");
        sb.append("How would you like to pay?");
        return sb.toString();
    }
}

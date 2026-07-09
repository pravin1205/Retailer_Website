package com.marketly.whatsapp.consumer;

import com.marketly.whatsapp.meta.MetaApiClient;
import com.marketly.whatsapp.service.TenantAccessTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka consumer that listens to order lifecycle events and sends WhatsApp
 * status update messages to customers.
 *
 * Topics consumed:
 *   order.order.created          — order placed confirmation
 *   order.order.status-changed   — packing, dispatched, delivered, etc.
 *   order.order.cancelled        — cancellation with refund info
 *
 * Filtering:
 *   Only events where payload.source == "WHATSAPP" result in WhatsApp messages.
 *   APP-sourced orders are handled by the notification-service (push/email/SMS).
 *   This ensures customers do not receive duplicate notifications.
 *
 * Tenant isolation:
 *   Every message is sent FROM the seller's own WhatsApp phone number using the
 *   seller's access token — retrieved via TenantAccessTokenService.
 *   The customer never sees Marketly — they see a message from their store.
 *
 * Design note on customerPhone:
 *   The Kafka payload includes customerPhone (added in Phase 0.2). If it is
 *   empty (APP-originated orders going through the standard checkout), the event
 *   is skipped. For WHATSAPP orders the phone is always populated because
 *   PaymentHandler sets it via the customer's WhatsApp number.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStatusConsumer {

    private final MetaApiClient             metaApiClient;
    private final TenantAccessTokenService  tokenService;

    // ── Order created ─────────────────────────────────────────────────────────

    @KafkaListener(
        topics = "order.order.created",
        groupId = "whatsapp-service-order-created-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCreated(Map<String, Object> event) {
        try {
            Map<String, Object> payload = extract(event);
            if (!isWhatsappOrder(payload)) return;

            String customerPhone  = str(payload.get("customerPhone"));
            String tenantId       = str(event.get("tenantId"));
            String orderNumber    = str(payload.get("orderNumber"));
            String totalAmount    = str(payload.get("totalAmount"));

            String accessToken    = tokenService.getAccessToken(tenantId);
            String phoneNumberId  = tokenService.getPhoneNumberId(tenantId);
            if (accessToken == null || phoneNumberId == null) return;

            String message = "✅ *Order Confirmed!*\n\n" +
                "Your order *" + orderNumber + "* has been received.\n" +
                "Total: ₹" + totalAmount + "\n\n" +
                "We'll notify you once the store starts packing. 📦";

            metaApiClient.sendText(phoneNumberId, accessToken, customerPhone, message);
            log.info("WhatsApp order-created notification sent: order={} customer={}", orderNumber, customerPhone);

        } catch (Exception e) {
            log.error("OrderStatusConsumer.onOrderCreated error: {}", e.getMessage(), e);
        }
    }

    // ── Status changed ────────────────────────────────────────────────────────

    @KafkaListener(
        topics = "order.order.status-changed",
        groupId = "whatsapp-service-status-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onStatusChanged(Map<String, Object> event) {
        try {
            Map<String, Object> payload = extract(event);
            if (!isWhatsappOrder(payload)) return;

            String customerPhone  = str(payload.get("customerPhone"));
            String tenantId       = str(event.get("tenantId"));
            String orderNumber    = str(payload.get("orderNumber"));
            String newStatus      = str(payload.get("newStatus"));
            String sellerNote     = str(payload.get("note"));

            String accessToken    = tokenService.getAccessToken(tenantId);
            String phoneNumberId  = tokenService.getPhoneNumberId(tenantId);
            if (accessToken == null || phoneNumberId == null) return;

            String message = buildStatusMessage(newStatus, orderNumber, sellerNote);
            if (message == null) return; // no notification for this transition

            metaApiClient.sendText(phoneNumberId, accessToken, customerPhone, message);
            log.info("WhatsApp status notification sent: order={} status={} customer={}",
                     orderNumber, newStatus, customerPhone);

        } catch (Exception e) {
            log.error("OrderStatusConsumer.onStatusChanged error: {}", e.getMessage(), e);
        }
    }

    // ── Order cancelled ───────────────────────────────────────────────────────

    @KafkaListener(
        topics = "order.order.cancelled",
        groupId = "whatsapp-service-cancelled-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCancelled(Map<String, Object> event) {
        try {
            Map<String, Object> payload = extract(event);
            if (!isWhatsappOrder(payload)) return;

            String customerPhone  = str(payload.get("customerPhone"));
            String tenantId       = str(event.get("tenantId"));
            String orderNumber    = str(payload.get("orderNumber"));
            String refundAmount   = str(payload.get("refundAmount"));

            String accessToken    = tokenService.getAccessToken(tenantId);
            String phoneNumberId  = tokenService.getPhoneNumberId(tenantId);
            if (accessToken == null || phoneNumberId == null) return;

            String message = "❌ *Order Cancelled*\n\n" +
                "Your order *" + orderNumber + "* has been cancelled.\n" +
                (refundAmount != null && !refundAmount.isBlank() && !"0".equals(refundAmount)
                    ? "Refund of ₹" + refundAmount + " will be processed within 5–7 business days.\n\n"
                    : "\n") +
                "Want to order something? Just tell me! 😊";

            metaApiClient.sendText(phoneNumberId, accessToken, customerPhone, message);
            log.info("WhatsApp cancellation notification sent: order={} customer={}", orderNumber, customerPhone);

        } catch (Exception e) {
            log.error("OrderStatusConsumer.onOrderCancelled error: {}", e.getMessage(), e);
        }
    }

    // ── Status message builder ────────────────────────────────────────────────

    /**
     * Returns a customer-facing WhatsApp message for each order status.
     * Returns null for statuses that should not trigger a notification.
     */
    private String buildStatusMessage(String status, String orderNumber, String sellerNote) {
        String noteSection = (sellerNote != null && !sellerNote.isBlank())
            ? "\n\n_Note from the store: " + sellerNote + "_" : "";

        return switch (status) {
            case "CONFIRMED" ->
                "✅ *Order Accepted!*\n\n" +
                "The store has confirmed your order *" + orderNumber + "*.\n" +
                "We'll start packing right away!" + noteSection;

            case "PACKING" ->
                "📦 *Being Packed!*\n\n" +
                "Your order *" + orderNumber + "* is being packed now." + noteSection;

            case "OUT_FOR_DELIVERY" ->
                "🛵 *Out for Delivery!*\n\n" +
                "Your order *" + orderNumber + "* is on its way!\n" +
                "Please be available to receive it." + noteSection + "\n\n" +
                "Questions? Just reply here.";

            case "DELIVERED" ->
                "🎉 *Delivered!*\n\n" +
                "Your order *" + orderNumber + "* has been delivered. Hope you enjoy it!\n\n" +
                "Reply ISSUE if there's any problem with your order.";

            case "RETURN_REQUESTED" ->
                "↩️ *Return Requested*\n\n" +
                "Your return request for order *" + orderNumber + "* has been received.\n" +
                "The store will contact you to arrange pickup." + noteSection;

            default -> null; // no notification for PLACED (covered by onOrderCreated), RETURNED, etc.
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> extract(Map<String, Object> event) {
        return (Map<String, Object>) event.get("payload");
    }

    private boolean isWhatsappOrder(Map<String, Object> payload) {
        if (payload == null) return false;
        String source = str(payload.get("source"));
        String phone  = str(payload.get("customerPhone"));
        // Only process WHATSAPP orders with a known customer phone number
        return "WHATSAPP".equalsIgnoreCase(source) && phone != null && !phone.isBlank();
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}

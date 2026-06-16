package com.marketly.notification.event;

import com.marketly.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes all relevant domain events and dispatches notifications.
 *
 * Topics handled:
 *  - order.order.created        → customer order confirmation
 *  - order.order.status-changed → customer status update
 *  - order.order.cancelled      → customer cancellation confirmation
 *  - product.inventory.low-stock-alert → store owner alert
 *  - identity.user.registered   → welcome email
 *  - tenant.tenant.activated    → owner onboarding email
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    // ── Order Created ─────────────────────────────────────────────────────

    @KafkaListener(
        topics = "order.order.created",
        groupId = "notification-service-order-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCreated(Map<String, Object> event) {
        try {
            Map<String, Object> payload = extract(event);
            UUID tenantId   = uuid(event.get("tenantId"));
            UUID customerId = uuid(payload.get("customerId"));
            String email    = str(payload.get("customerEmail"));
            String orderNo  = str(payload.get("orderNumber"));
            String total    = str(payload.get("totalAmount"));
            UUID   orderId  = uuid(payload.get("orderId"));

            notificationService.dispatchAll(
                tenantId, customerId, email,
                "ORDER_UPDATE",
                "Order Placed — " + orderNo,
                "Your order " + orderNo + " has been placed successfully. Total: ₹" + total +
                ". We'll notify you once the store confirms.",
                orderId, "ORDER"
            );
        } catch (Exception e) {
            log.error("Failed to send order-created notification: {}", e.getMessage(), e);
        }
    }

    // ── Order Status Changed ──────────────────────────────────────────────

    @KafkaListener(
        topics = "order.order.status-changed",
        groupId = "notification-service-status-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderStatusChanged(Map<String, Object> event) {
        try {
            Map<String, Object> payload = extract(event);
            UUID   tenantId   = uuid(event.get("tenantId"));
            UUID   customerId = uuid(payload.get("customerId"));
            String email      = str(payload.get("customerEmail"));
            String orderNo    = str(payload.get("orderNumber"));
            String newStatus  = str(payload.get("newStatus"));
            UUID   orderId    = uuid(payload.get("orderId"));

            String title = statusTitle(newStatus, orderNo);
            String body  = statusBody(newStatus, orderNo);
            if (title == null) return; // no notification for this transition

            notificationService.dispatchAll(
                tenantId, customerId, email,
                "ORDER_UPDATE", title, body, orderId, "ORDER"
            );
        } catch (Exception e) {
            log.error("Failed to send order-status notification: {}", e.getMessage(), e);
        }
    }

    // ── Order Cancelled ───────────────────────────────────────────────────

    @KafkaListener(
        topics = "order.order.cancelled",
        groupId = "notification-service-cancel-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCancelled(Map<String, Object> event) {
        try {
            Map<String, Object> payload = extract(event);
            UUID   tenantId   = uuid(event.get("tenantId"));
            UUID   customerId = uuid(payload.get("customerId"));
            String email      = str(payload.get("customerEmail"));
            String orderNo    = str(payload.get("orderNumber"));
            String refund     = str(payload.get("refundAmount"));
            UUID   orderId    = uuid(payload.get("orderId"));

            notificationService.dispatchAll(
                tenantId, customerId, email,
                "ORDER_UPDATE",
                "Order Cancelled — " + orderNo,
                "Your order " + orderNo + " has been cancelled. " +
                "Refund of ₹" + refund + " will be processed within 5-7 business days.",
                orderId, "ORDER"
            );
        } catch (Exception e) {
            log.error("Failed to send order-cancelled notification: {}", e.getMessage(), e);
        }
    }

    // ── Low Stock Alert ───────────────────────────────────────────────────

    @KafkaListener(
        topics = "product.inventory.low-stock-alert",
        groupId = "notification-service-inventory-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onLowStock(Map<String, Object> event) {
        try {
            Map<String, Object> payload = extract(event);
            UUID   tenantId    = uuid(event.get("tenantId"));
            String productName = str(payload.get("productName"));
            int    currentQty  = ((Number) payload.get("currentQuantity")).intValue();
            int    threshold   = ((Number) payload.get("threshold")).intValue();
            UUID   productId   = uuid(payload.get("productId"));
            String ownerEmail  = str(payload.getOrDefault("ownerEmail", ""));

            // For low-stock, recipientId = tenantId (store owner)
            // In a real system, fetch owner's userId from tenant-service via Feign
            notificationService.dispatch(
                tenantId, tenantId, ownerEmail,
                "IN_APP",
                "LOW_STOCK",
                "Low Stock Alert — " + productName,
                "'" + productName + "' has only " + currentQty +
                " units left (threshold: " + threshold + "). Restock soon.",
                productId, "PRODUCT"
            );
        } catch (Exception e) {
            log.error("Failed to send low-stock notification: {}", e.getMessage(), e);
        }
    }

    // ── Welcome Email ─────────────────────────────────────────────────────

    @KafkaListener(
        topics = "identity.user.registered",
        groupId = "notification-service-identity-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserRegistered(Map<String, Object> event) {
        try {
            Map<String, Object> payload = extract(event);
            UUID   tenantId = event.get("tenantId") != null ? uuid(event.get("tenantId")) : null;
            UUID   userId   = uuid(payload.get("userId"));
            String email    = str(payload.get("email"));

            notificationService.dispatch(
                tenantId, userId, email,
                "EMAIL",
                "WELCOME",
                "Welcome to Marketly!",
                "Hi! Your account has been created. Start shopping your favorite local stores.",
                null, null
            );
        } catch (Exception e) {
            log.error("Failed to send welcome notification: {}", e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> extract(Map<String, Object> event) {
        return (Map<String, Object>) event.get("payload");
    }

    private UUID uuid(Object o) {
        return o == null ? null : UUID.fromString(o.toString());
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private String statusTitle(String status, String orderNo) {
        return switch (status) {
            case "CONFIRMED"        -> "Order Confirmed — " + orderNo;
            case "PACKING"          -> "Being Packed — " + orderNo;
            case "OUT_FOR_DELIVERY" -> "Out for Delivery — " + orderNo;
            case "DELIVERED"        -> "Delivered — " + orderNo;
            default -> null;
        };
    }

    private String statusBody(String status, String orderNo) {
        return switch (status) {
            case "CONFIRMED"        -> "Great news! The store has confirmed your order " + orderNo + ".";
            case "PACKING"          -> "Your order " + orderNo + " is being packed and will ship soon.";
            case "OUT_FOR_DELIVERY" -> "Your order " + orderNo + " is out for delivery. Expect it shortly!";
            case "DELIVERED"        -> "Your order " + orderNo + " has been delivered. Enjoy!";
            default -> null;
        };
    }
}

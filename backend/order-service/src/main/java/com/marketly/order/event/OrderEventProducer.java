package com.marketly.order.event;

import com.marketly.order.entity.Order;
import com.marketly.order.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private static final String TOPIC_ORDER_CREATED       = "order.order.created";
    private static final String TOPIC_ORDER_STATUS_CHANGED = "order.order.status-changed";
    private static final String TOPIC_ORDER_CANCELLED     = "order.order.cancelled";
    private static final String TOPIC_PAYMENT_COMPLETED   = "order.payment.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(Order order) {
        List<Map<String, Object>> items = order.getItems().stream()
            .map(i -> Map.<String, Object>of(
                "productId",  i.getProductId().toString(),
                "productName", i.getProductName(),
                "quantity",   i.getQuantity(),
                "unitPrice",  i.getUnitPrice().toString(),
                "lineTotal",  i.getLineTotal().toString()
            )).toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId",       order.getId().toString());
        payload.put("orderNumber",   order.getOrderNumber());
        payload.put("tenantId",      order.getTenantId().toString());
        payload.put("customerId",    order.getCustomerId().toString());
        payload.put("status",        order.getStatus());
        payload.put("subtotal",      order.getSubtotal().toString());
        payload.put("discountAmount", order.getDiscountAmount().toString());
        payload.put("deliveryCharge", order.getDeliveryCharge().toString());
        payload.put("totalAmount",   order.getTotalAmount().toString());
        payload.put("couponCode",    order.getCouponCode());
        payload.put("paymentMethod", order.getPaymentMethod());
        payload.put("items",         items);
        payload.put("placedAt",      order.getPlacedAt().toString());

        send(TOPIC_ORDER_CREATED, order.getId().toString(), order.getTenantId().toString(), payload);
    }

    public void publishStatusChanged(Order order, String previousStatus, String note) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId",        order.getId().toString());
        payload.put("orderNumber",    order.getOrderNumber());
        payload.put("tenantId",       order.getTenantId().toString());
        payload.put("customerId",     order.getCustomerId().toString());
        payload.put("previousStatus", previousStatus);
        payload.put("newStatus",      order.getStatus());
        payload.put("note",           note);
        payload.put("changedAt",      Instant.now().toString());

        send(TOPIC_ORDER_STATUS_CHANGED, order.getId().toString(),
             order.getTenantId().toString(), payload);
    }

    public void publishOrderCancelled(Order order) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId",      order.getId().toString());
        payload.put("orderNumber",  order.getOrderNumber());
        payload.put("tenantId",     order.getTenantId().toString());
        payload.put("customerId",   order.getCustomerId().toString());
        payload.put("reason",       order.getCancellationReason());
        payload.put("cancelledAt",  order.getCancelledAt().toString());
        payload.put("refundAmount", order.getTotalAmount().toString());
        payload.put("items", order.getItems().stream()
            .map(i -> Map.of("productId", i.getProductId().toString(),
                             "quantity",  i.getQuantity()))
            .toList());

        send(TOPIC_ORDER_CANCELLED, order.getId().toString(),
             order.getTenantId().toString(), payload);
    }

    public void publishPaymentCompleted(Order order, String gatewayPaymentId,
                                        String method, String gateway) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId",          order.getId().toString());
        payload.put("orderNumber",      order.getOrderNumber());
        payload.put("tenantId",         order.getTenantId().toString());
        payload.put("customerId",       order.getCustomerId().toString());
        payload.put("amount",           order.getTotalAmount().toString());
        payload.put("currency",         "INR");
        payload.put("method",           method);
        payload.put("gateway",          gateway);
        payload.put("gatewayPaymentId", gatewayPaymentId);
        payload.put("paidAt",           Instant.now().toString());

        send(TOPIC_PAYMENT_COMPLETED, order.getId().toString(),
             order.getTenantId().toString(), payload);
    }

    private void send(String topic, String key, String tenantId, Map<String, Object> payload) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType",    topic);
        event.put("eventVersion", "1.0");
        event.put("occurredAt",   Instant.now().toString());
        event.put("tenantId",     tenantId);
        event.put("producedBy",   "order-service");
        event.put("payload",      payload);

        kafkaTemplate.send(topic, key, event)
            .whenComplete((r, ex) -> {
                if (ex != null)
                    log.error("Failed to publish to {}: {}", topic, ex.getMessage());
            });
    }
}

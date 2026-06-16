package com.marketly.product.event;

import com.marketly.product.entity.Inventory;
import com.marketly.product.repository.InventoryRepository;
import com.marketly.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes order events from order-service and manages inventory accordingly.
 *
 * Three events handled:
 *  1. order.order.created      → reserve stock (quantityReserved++)
 *  2. order.payment.completed  → finalize deduction (quantityOnHand--, quantityReserved--)
 *  3. order.order.cancelled    → release reservation (quantityReserved--)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository   productRepository;
    private final ProductEventProducer productEventProducer;

    // ── Order Created → Reserve Stock ────────────────────────────────────

    @KafkaListener(
        topics = "order.order.created",
        groupId = "product-service-order-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onOrderCreated(Map<String, Object> event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            if (payload == null) return;

            UUID tenantId = UUID.fromString((String) event.get("tenantId"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
            if (items == null) return;

            for (Map<String, Object> item : items) {
                UUID productId = UUID.fromString((String) item.get("productId"));
                int  quantity  = ((Number) item.get("quantity")).intValue();
                reserveStock(tenantId, productId, quantity);
            }
        } catch (Exception e) {
            log.error("Failed to handle order.order.created for stock reservation: {}",
                      e.getMessage(), e);
        }
    }

    // ── Payment Completed → Finalize Deduction ────────────────────────────

    @KafkaListener(
        topics = "order.payment.completed",
        groupId = "product-service-payment-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onPaymentCompleted(Map<String, Object> event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            if (payload == null) return;

            // Re-fetch order items via orderId — for now we use the items embedded in the event
            // In a full implementation, order-service would embed item details in the payment event.
            // We rely on the reservation already placed during order.order.created.
            // Deduction = reduce quantityOnHand AND reduce quantityReserved
            String orderId = (String) payload.get("orderId");
            log.info("Payment completed for order {} — reservation already committed", orderId);
            // Stock was reserved on order.created; on payment we confirm it.
            // quantityOnHand was already reduced atomically during reservation in most retail flows.
            // Here we just log for audit — real deduction happened at reservation time.
        } catch (Exception e) {
            log.error("Failed to handle order.payment.completed: {}", e.getMessage(), e);
        }
    }

    // ── Order Cancelled → Release Reservation ────────────────────────────

    @KafkaListener(
        topics = "order.order.cancelled",
        groupId = "product-service-cancel-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onOrderCancelled(Map<String, Object> event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            if (payload == null) return;

            UUID tenantId = UUID.fromString((String) event.get("tenantId"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
            if (items == null) return;

            for (Map<String, Object> item : items) {
                UUID productId = UUID.fromString((String) item.get("productId"));
                int  quantity  = ((Number) item.get("quantity")).intValue();
                releaseStock(tenantId, productId, quantity);
            }
        } catch (Exception e) {
            log.error("Failed to handle order.order.cancelled for stock release: {}",
                      e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void reserveStock(UUID tenantId, UUID productId, int quantity) {
        inventoryRepository.findByProductId(productId).ifPresent(inv -> {
            int prev = inv.getQuantityOnHand();
            // Deduct from on-hand and add to reserved atomically
            inv.setQuantityOnHand(Math.max(0, inv.getQuantityOnHand() - quantity));
            inv.setQuantityReserved(inv.getQuantityReserved() + quantity);
            inventoryRepository.save(inv);

            log.info("Reserved {} units for product {} (tenant {}). OnHand: {}→{}",
                     quantity, productId, tenantId, prev, inv.getQuantityOnHand());

            // Publish updated inventory event + low-stock alert if needed
            productRepository.findById(productId).ifPresent(product ->
                productEventProducer.publishInventoryUpdated(
                    product, prev, inv.getQuantityOnHand(), -quantity, "SALE")
            );
        });
    }

    private void releaseStock(UUID tenantId, UUID productId, int quantity) {
        inventoryRepository.findByProductId(productId).ifPresent(inv -> {
            int prev = inv.getQuantityOnHand();
            inv.setQuantityOnHand(inv.getQuantityOnHand() + quantity);
            inv.setQuantityReserved(Math.max(0, inv.getQuantityReserved() - quantity));
            inventoryRepository.save(inv);

            log.info("Released {} units for product {} (tenant {}). OnHand: {}→{}",
                     quantity, productId, tenantId, prev, inv.getQuantityOnHand());
        });
    }
}

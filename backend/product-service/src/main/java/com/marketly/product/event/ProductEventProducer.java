package com.marketly.product.event;

import com.marketly.product.entity.Inventory;
import com.marketly.product.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventProducer {

    private static final String TOPIC_PRODUCT_CREATED  = "product.product.created";
    private static final String TOPIC_INVENTORY_UPDATED = "product.inventory.updated";
    private static final String TOPIC_LOW_STOCK         = "product.inventory.low-stock-alert";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishProductCreated(Product product, Inventory inventory) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", TOPIC_PRODUCT_CREATED);
        event.put("eventVersion", "1.0");
        event.put("occurredAt", Instant.now().toString());
        event.put("tenantId", product.getTenantId().toString());
        event.put("producedBy", "product-service");
        event.put("payload", Map.of(
            "productId",    product.getId().toString(),
            "tenantId",     product.getTenantId().toString(),
            "name",         product.getName(),
            "sku",          product.getSku() != null ? product.getSku() : "",
            "price",        product.getPrice().toString(),
            "initialStock", inventory.getQuantityOnHand()
        ));
        send(TOPIC_PRODUCT_CREATED, product.getId().toString(), event);
    }

    public void publishInventoryUpdated(Product product, int previousQty,
                                        int newQty, int delta, String reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", TOPIC_INVENTORY_UPDATED);
        event.put("eventVersion", "1.0");
        event.put("occurredAt", Instant.now().toString());
        event.put("tenantId", product.getTenantId().toString());
        event.put("producedBy", "product-service");
        event.put("payload", Map.of(
            "productId",       product.getId().toString(),
            "tenantId",        product.getTenantId().toString(),
            "previousQuantity", previousQty,
            "newQuantity",     newQty,
            "delta",           delta,
            "reason",          reason
        ));
        send(TOPIC_INVENTORY_UPDATED, product.getId().toString(), event);

        if (newQty <= product.getInventory().getLowStockThreshold()) {
            publishLowStockAlert(product, newQty);
        }
    }

    private void publishLowStockAlert(Product product, int currentQty) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", TOPIC_LOW_STOCK);
        event.put("eventVersion", "1.0");
        event.put("occurredAt", Instant.now().toString());
        event.put("tenantId", product.getTenantId().toString());
        event.put("producedBy", "product-service");
        event.put("payload", Map.of(
            "productId",       product.getId().toString(),
            "productName",     product.getName(),
            "tenantId",        product.getTenantId().toString(),
            "currentQuantity", currentQty,
            "threshold",       product.getInventory().getLowStockThreshold(),
            "sku",             product.getSku() != null ? product.getSku() : ""
        ));
        send(TOPIC_LOW_STOCK, product.getId().toString(), event);
    }

    private void send(String topic, String key, Map<String, Object> event) {
        kafkaTemplate.send(topic, key, event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish to {}: {}", topic, ex.getMessage());
            });
    }
}

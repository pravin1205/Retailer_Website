package com.marketly.analytics.event;

import com.marketly.analytics.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates order events into analytics summaries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final DashboardService dashboardService;

    @KafkaListener(
        topics = "order.order.created",
        groupId = "analytics-service-order-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCreated(Map<String, Object> event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            if (payload == null) return;

            UUID       tenantId     = UUID.fromString((String) event.get("tenantId"));
            BigDecimal total        = new BigDecimal(payload.get("totalAmount").toString());
            BigDecimal discount     = payload.get("discountAmount") != null
                ? new BigDecimal(payload.get("discountAmount").toString()) : BigDecimal.ZERO;

            dashboardService.recordOrder(tenantId, total, discount, false);
        } catch (Exception e) {
            log.error("Analytics: failed to process order.order.created: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "order.order.cancelled",
        groupId = "analytics-service-cancel-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCancelled(Map<String, Object> event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            if (payload == null) return;

            UUID       tenantId = UUID.fromString((String) event.get("tenantId"));
            BigDecimal amount   = new BigDecimal(payload.get("refundAmount").toString());

            dashboardService.recordCancellation(tenantId, amount);
        } catch (Exception e) {
            log.error("Analytics: failed to process order.order.cancelled: {}", e.getMessage(), e);
        }
    }
}

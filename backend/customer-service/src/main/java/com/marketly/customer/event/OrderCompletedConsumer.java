package com.marketly.customer.event;

import com.marketly.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Listens to order.payment.completed events.
 * Awards loyalty points to the customer after a successful payment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCompletedConsumer {

    private final CustomerService customerService;

    @KafkaListener(
        topics = "order.payment.completed",
        groupId = "customer-service-order-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handle(Map<String, Object> event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            if (payload == null) return;

            String tenantIdStr   = (String) event.get("tenantId");
            String customerIdStr = (String) payload.get("customerId");
            Object amountObj     = payload.get("amount");

            if (tenantIdStr == null || customerIdStr == null || amountObj == null) return;

            BigDecimal amount = new BigDecimal(amountObj.toString());

            customerService.awardLoyaltyPoints(
                UUID.fromString(tenantIdStr),
                UUID.fromString(customerIdStr),
                amount
            );
        } catch (Exception e) {
            log.error("Failed to handle PaymentCompletedEvent: {}", e.getMessage(), e);
        }
    }
}

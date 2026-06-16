package com.marketly.customer.event;

import com.marketly.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Listens to identity.user.registered events.
 * Creates a Customer profile in this service when a new user registers,
 * scoped to the tenant they registered at.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredConsumer {

    private final CustomerService customerService;

    @KafkaListener(
        topics = "identity.user.registered",
        groupId = "customer-service-identity-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handle(Map<String, Object> event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            if (payload == null) return;

            String tenantSlugRaw = (String) payload.get("tenantSlug");
            if (tenantSlugRaw == null || tenantSlugRaw.isBlank()) {
                // Registration without tenant context — no customer profile needed yet
                return;
            }

            String userIdStr = (String) payload.get("userId");
            String tenantId  = (String) event.get("tenantId");
            if (userIdStr == null || tenantId == null) return;

            customerService.getOrCreate(
                UUID.fromString(tenantId),
                UUID.fromString(userIdStr),
                (String) payload.get("email"),
                (String) payload.getOrDefault("firstName", ""),
                (String) payload.getOrDefault("lastName", ""),
                (String) payload.getOrDefault("phone", "")
            );
        } catch (Exception e) {
            log.error("Failed to handle UserRegisteredEvent: {}", e.getMessage(), e);
        }
    }
}

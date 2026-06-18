package com.marketly.customer.event;

import com.marketly.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

/**
 * Listens to identity.user.registered events.
 * Creates a Customer profile in this service when a new user registers,
 * scoped to the tenant they registered at.
 *
 * Fix: event root now carries tenantSlug (not tenantId, since identity-service
 * has no Feign client to tenant-service). This consumer resolves slug → UUID
 * via a lightweight GET call to tenant-service.
 */
@Component
@Slf4j
public class UserRegisteredConsumer {

    private final CustomerService customerService;
    private final RestClient       restClient;

    public UserRegisteredConsumer(
            CustomerService customerService,
            @Value("${app.tenant-service-url:http://localhost:8082}") String tenantServiceUrl) {
        this.customerService = customerService;
        this.restClient = RestClient.builder()
            .baseUrl(tenantServiceUrl)
            .build();
    }

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

            // Read tenantSlug from root level (added in fix) or fallback to payload
            String tenantSlug = (String) event.getOrDefault("tenantSlug",
                                          payload.getOrDefault("tenantSlug", ""));
            if (tenantSlug == null || tenantSlug.isBlank()) {
                // Seller-only registration (TENANT_OWNER) or no tenant context — skip
                log.debug("UserRegisteredEvent with no tenantSlug — skipping customer profile creation");
                return;
            }

            String userIdStr = (String) payload.get("userId");
            if (userIdStr == null) {
                log.warn("UserRegisteredEvent missing userId — skipping");
                return;
            }

            // Resolve slug → UUID via tenant-service REST call
            UUID tenantId = resolveTenantId(tenantSlug);
            if (tenantId == null) {
                log.warn("Could not resolve tenantId for slug='{}' — skipping customer profile", tenantSlug);
                return;
            }

            customerService.getOrCreate(
                tenantId,
                UUID.fromString(userIdStr),
                (String) payload.getOrDefault("email", ""),
                (String) payload.getOrDefault("firstName", ""),
                (String) payload.getOrDefault("lastName", ""),
                (String) payload.getOrDefault("phone", "")
            );

        } catch (Exception e) {
            log.error("Failed to handle UserRegisteredEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Calls GET /api/v1/tenants/{slug} on tenant-service and extracts the UUID.
     * Returns null if the tenant is not found or the call fails.
     */
    @SuppressWarnings("unchecked")
    private UUID resolveTenantId(String slug) {
        try {
            Map<String, Object> response = restClient.get()
                .uri("/api/v1/tenants/{slug}", slug)
                .retrieve()
                .body(Map.class);

            if (response == null) return null;

            // Response envelope: { "success": true, "data": { "id": "uuid", ... } }
            Object data = response.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Object id = dataMap.get("id");
                if (id != null) return UUID.fromString(id.toString());
            }
            return null;
        } catch (RestClientException e) {
            log.warn("Failed to resolve tenantId for slug='{}': {}", slug, e.getMessage());
            return null;
        }
    }
}

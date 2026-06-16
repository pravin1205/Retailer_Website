package com.marketly.tenant.event;

import com.marketly.tenant.entity.Tenant;
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
public class TenantEventProducer {

    private static final String TOPIC_CREATED   = "tenant.tenant.created";
    private static final String TOPIC_ACTIVATED = "tenant.tenant.activated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishTenantCreated(Tenant tenant, String ownerEmail) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", TOPIC_CREATED);
        event.put("eventVersion", "1.0");
        event.put("occurredAt", Instant.now().toString());
        event.put("producedBy", "tenant-service");
        event.put("payload", Map.of(
            "tenantId",         tenant.getId().toString(),
            "slug",             tenant.getSlug(),
            "name",             tenant.getName(),
            "category",         tenant.getCategory(),
            "ownerUserId",      tenant.getOwnerUserId().toString(),
            "ownerEmail",       ownerEmail != null ? ownerEmail : "",
            "subscriptionPlan", tenant.getSubscriptionPlan()
        ));
        kafkaTemplate.send(TOPIC_CREATED, tenant.getId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish TenantCreatedEvent: {}", ex.getMessage());
            });
    }

    public void publishTenantActivated(Tenant tenant) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", TOPIC_ACTIVATED);
        event.put("eventVersion", "1.0");
        event.put("occurredAt", Instant.now().toString());
        event.put("producedBy", "tenant-service");
        event.put("payload", Map.of(
            "tenantId",    tenant.getId().toString(),
            "slug",        tenant.getSlug(),
            "activatedAt", Instant.now().toString()
        ));
        kafkaTemplate.send(TOPIC_ACTIVATED, tenant.getId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish TenantActivatedEvent: {}", ex.getMessage());
            });
    }
}

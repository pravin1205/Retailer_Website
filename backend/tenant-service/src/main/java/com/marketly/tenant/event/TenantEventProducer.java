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

    private static final String TOPIC_CREATED       = "tenant.tenant.created";
    private static final String TOPIC_ACTIVATED     = "tenant.tenant.activated";
    private static final String TOPIC_KYC_SUBMITTED = "tenant.seller.kyc-submitted";
    private static final String TOPIC_APPROVED      = "tenant.seller.approved";
    private static final String TOPIC_REJECTED      = "tenant.seller.rejected";

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

    public void publishKycSubmitted(Tenant tenant, String ownerEmail) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", TOPIC_KYC_SUBMITTED);
        event.put("eventVersion", "1.0");
        event.put("occurredAt", Instant.now().toString());
        event.put("producedBy", "tenant-service");
        event.put("tenantId", tenant.getId().toString());
        event.put("payload", Map.of(
            "tenantId",    tenant.getId().toString(),
            "slug",        tenant.getSlug(),
            "storeName",   tenant.getName(),
            "ownerUserId", tenant.getOwnerUserId().toString(),
            "ownerEmail",  ownerEmail != null ? ownerEmail : "",
            "submittedAt", Instant.now().toString()
        ));
        kafkaTemplate.send(TOPIC_KYC_SUBMITTED, tenant.getId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish KycSubmittedEvent: {}", ex.getMessage());
            });
    }

    public void publishSellerApproved(Tenant tenant, String ownerEmail) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", TOPIC_APPROVED);
        event.put("eventVersion", "1.0");
        event.put("occurredAt", Instant.now().toString());
        event.put("producedBy", "tenant-service");
        event.put("tenantId", tenant.getId().toString());
        event.put("payload", Map.of(
            "tenantId",    tenant.getId().toString(),
            "slug",        tenant.getSlug(),
            "storeName",   tenant.getName(),
            "ownerUserId", tenant.getOwnerUserId().toString(),
            "ownerEmail",  ownerEmail != null ? ownerEmail : "",
            "storeUrl",    "/s/" + tenant.getSlug(),
            "approvedAt",  Instant.now().toString()
        ));
        kafkaTemplate.send(TOPIC_APPROVED, tenant.getId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish SellerApprovedEvent: {}", ex.getMessage());
            });
    }

    public void publishSellerRejected(Tenant tenant, String ownerEmail, String reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", TOPIC_REJECTED);
        event.put("eventVersion", "1.0");
        event.put("occurredAt", Instant.now().toString());
        event.put("producedBy", "tenant-service");
        event.put("tenantId", tenant.getId().toString());
        event.put("payload", Map.of(
            "tenantId",    tenant.getId().toString(),
            "slug",        tenant.getSlug(),
            "storeName",   tenant.getName(),
            "ownerUserId", tenant.getOwnerUserId().toString(),
            "ownerEmail",  ownerEmail != null ? ownerEmail : "",
            "reason",      reason != null ? reason : "KYC verification failed.",
            "rejectedAt",  Instant.now().toString()
        ));
        kafkaTemplate.send(TOPIC_REJECTED, tenant.getId().toString(), event)
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to publish SellerRejectedEvent: {}", ex.getMessage());
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

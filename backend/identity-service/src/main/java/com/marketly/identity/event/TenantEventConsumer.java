package com.marketly.identity.event;

import com.marketly.identity.entity.Role;
import com.marketly.identity.entity.User;
import com.marketly.identity.entity.UserTenantRole;
import com.marketly.identity.repository.RoleRepository;
import com.marketly.identity.repository.UserRepository;
import com.marketly.identity.repository.UserTenantRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Listens to tenant lifecycle events and updates RBAC in identity-service.
 *
 * On tenant.seller.approved:
 *   - Inserts a TENANT_OWNER UserTenantRole row for the store owner.
 *   - This gives the seller's JWT the TENANT_OWNER claim, unlocking /s/{slug}/admin.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantEventConsumer {

    private final UserRepository           userRepository;
    private final RoleRepository           roleRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;

    @KafkaListener(
        topics = "tenant.seller.approved",
        groupId = "identity-service-seller-approved-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onSellerApproved(Map<String, Object> event) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            if (payload == null) {
                log.warn("tenant.seller.approved event has no payload — skipping role assignment");
                return;
            }

            String tenantIdStr   = str(event.get("tenantId"));
            String ownerUserIdStr = str(payload.get("ownerUserId"));

            if (tenantIdStr.isBlank() || ownerUserIdStr.isBlank()) {
                log.warn("tenant.seller.approved missing tenantId or ownerUserId — skipping");
                return;
            }

            UUID tenantId   = UUID.fromString(tenantIdStr);
            UUID ownerUserId = UUID.fromString(ownerUserIdStr);

            // Idempotent: skip if role already exists
            boolean alreadyAssigned = userTenantRoleRepository
                .existsByUserIdAndTenantIdAndRoleNameAndDeletedAtIsNull(
                    ownerUserId, tenantId, "TENANT_OWNER");
            if (alreadyAssigned) {
                log.debug("TENANT_OWNER role already assigned for user={} tenant={}", ownerUserId, tenantId);
                return;
            }

            User owner = userRepository.findById(ownerUserId).orElse(null);
            if (owner == null) {
                log.error("Cannot assign TENANT_OWNER role — user {} not found", ownerUserId);
                return;
            }

            Role tenantOwnerRole = roleRepository.findByName("TENANT_OWNER")
                .orElseThrow(() -> new IllegalStateException("TENANT_OWNER role not seeded"));

            UserTenantRole utr = UserTenantRole.builder()
                .user(owner)
                .tenantId(tenantId)
                .role(tenantOwnerRole)
                .build();
            userTenantRoleRepository.save(utr);

            log.info("TENANT_OWNER role assigned: userId={} tenantId={}", ownerUserId, tenantId);

        } catch (Exception e) {
            log.error("Failed to assign TENANT_OWNER on seller approved: {}", e.getMessage(), e);
        }
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }
}

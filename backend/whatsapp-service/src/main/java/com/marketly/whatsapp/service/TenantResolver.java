package com.marketly.whatsapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Resolves a Meta phone_number_id to the Marketly tenantId (UUID).
 *
 * This mapping is the core routing mechanism: every inbound WhatsApp webhook
 * carries the phone_number_id of the number that received the message. By
 * resolving this to a tenantId, the service knows which seller store the
 * customer is ordering from.
 *
 * Resolution strategy:
 *  1. Check Redis cache  (TTL: 1 hour — avoids a network call on every message)
 *  2. On cache miss, call tenant-service: GET /api/v1/tenants/by-whatsapp/{phoneNumberId}
 *  3. Cache the result and return
 *
 * Cache invalidation is not needed because:
 *  - A phone_number_id is assigned once and is permanent per connection.
 *  - If a seller disconnects, new messages stop arriving on that number.
 *  - Reconnection uses the same or a new phone_number_id; the cache will
 *    miss on the new ID and refill naturally.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantResolver {

    private static final String CACHE_KEY_PREFIX    = "wa:tenant:";
    private static final long   CACHE_TTL_HOURS     = 1L;
    private static final String UNKNOWN_TENANT_VALUE = "UNKNOWN";

    private final StringRedisTemplate redisTemplate;
    private final WebClient.Builder   webClientBuilder;

    @Value("${app.services.tenant-service-url:http://localhost:8082}")
    private String tenantServiceUrl;

    /**
     * Resolves a phone_number_id to a tenantId.
     *
     * @param phoneNumberId Meta's internal identifier for a WhatsApp phone number.
     * @return The UUID of the Marketly tenant that owns this number.
     * @throws TenantNotFoundException if no tenant is registered for this number.
     */
    public UUID resolve(String phoneNumberId) {
        // 1. Check Redis cache
        String cached = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + phoneNumberId);
        if (cached != null) {
            if (UNKNOWN_TENANT_VALUE.equals(cached)) {
                // Previously determined to be unmapped — fail fast without network call
                throw new TenantNotFoundException(phoneNumberId);
            }
            log.debug("TenantResolver cache hit for phoneNumberId={}", phoneNumberId);
            return UUID.fromString(cached);
        }

        // 2. Call tenant-service
        log.debug("TenantResolver cache miss — calling tenant-service for phoneNumberId={}", phoneNumberId);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClientBuilder
                .baseUrl(tenantServiceUrl)
                .build()
                .get()
                .uri("/api/v1/tenants/by-whatsapp/{phoneNumberId}", phoneNumberId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                cacheMiss(phoneNumberId);
                throw new TenantNotFoundException(phoneNumberId);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null || data.get("tenantId") == null) {
                cacheMiss(phoneNumberId);
                throw new TenantNotFoundException(phoneNumberId);
            }

            UUID tenantId = UUID.fromString(data.get("tenantId").toString());

            // 3. Cache the resolved tenantId
            redisTemplate.opsForValue().set(
                CACHE_KEY_PREFIX + phoneNumberId,
                tenantId.toString(),
                CACHE_TTL_HOURS,
                TimeUnit.HOURS
            );
            log.info("TenantResolver resolved phoneNumberId={} → tenantId={}", phoneNumberId, tenantId);
            return tenantId;

        } catch (WebClientResponseException.NotFound e) {
            cacheMiss(phoneNumberId);
            throw new TenantNotFoundException(phoneNumberId);
        } catch (WebClientResponseException e) {
            log.error("TenantResolver: tenant-service error for phoneNumberId={}: {} {}",
                      phoneNumberId, e.getStatusCode(), e.getMessage());
            // Do NOT cache on transient errors — let the next message retry
            throw new RuntimeException("Failed to resolve tenant for phoneNumberId: " + phoneNumberId, e);
        }
    }

    /**
     * Explicitly evicts the cached mapping for a phone number.
     * Called when a seller disconnects their WhatsApp number so that
     * subsequent messages on that number are rejected promptly.
     */
    public void evict(String phoneNumberId) {
        redisTemplate.delete(CACHE_KEY_PREFIX + phoneNumberId);
        log.debug("TenantResolver evicted cache for phoneNumberId={}", phoneNumberId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Caches a negative result with a shorter TTL to avoid thundering herd. */
    private void cacheMiss(String phoneNumberId) {
        redisTemplate.opsForValue().set(
            CACHE_KEY_PREFIX + phoneNumberId,
            UNKNOWN_TENANT_VALUE,
            5L,
            TimeUnit.MINUTES   // short TTL — the seller might reconnect soon
        );
    }

    // ── Exception ─────────────────────────────────────────────────────────────

    /**
     * Thrown when the phone_number_id in a Meta webhook does not match
     * any active tenant in the platform. This is the normal path for test
     * messages or numbers that were disconnected.
     */
    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(String phoneNumberId) {
            super("No tenant found for WhatsApp phone_number_id: " + phoneNumberId);
        }
    }
}

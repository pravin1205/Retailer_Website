package com.marketly.whatsapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Resolves a tenant's Meta WhatsApp access token and phone_number_id,
 * both of which are required to send messages FROM the seller's number.
 *
 * Storage:
 *   - Both values are stored in tenant_settings via the tenant-service
 *     when the seller connects their WhatsApp number.
 *   - The access_token is AES-256 encrypted at rest.
 *   - This service decrypts the token at runtime.
 *
 * Caching:
 *   - Resolved values are cached in Redis for 1 hour to avoid repeated
 *     calls to tenant-service on every status notification.
 *
 * Key names in tenant_settings:
 *   whatsapp_phone_number_id  — Meta's internal phone number identifier
 *   whatsapp_access_token     — AES-256 encrypted Meta access token
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantAccessTokenService {

    private static final String CACHE_PREFIX_TOKEN  = "wa:token:";
    private static final String CACHE_PREFIX_PHONE  = "wa:phoneid:";
    private static final long   CACHE_TTL_HOURS     = 1L;

    private final StringRedisTemplate redisTemplate;
    private final WebClient.Builder   webClientBuilder;

    @Value("${app.services.tenant-service-url:http://localhost:8082}")
    private String tenantServiceUrl;

    @Value("${app.token-encryption-key:dev-32-char-encryption-key-here!!}")
    private String encryptionKey;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the decrypted Meta access token for the given tenant.
     * Used when sending WhatsApp messages from the seller's number.
     *
     * @param tenantId UUID string of the tenant.
     * @return Decrypted access token, or null if not configured.
     */
    public String getAccessToken(String tenantId) {
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX_TOKEN + tenantId);
        if (cached != null) return cached;

        Map<String, String> settings = fetchSettings(tenantId);
        if (settings == null) return null;

        String encryptedToken = settings.get("whatsapp_access_token");
        if (encryptedToken == null || encryptedToken.isBlank()) {
            log.warn("No WhatsApp access token configured for tenant={}", tenantId);
            return null;
        }

        try {
            String token = decrypt(encryptedToken);
            redisTemplate.opsForValue().set(
                CACHE_PREFIX_TOKEN + tenantId, token, CACHE_TTL_HOURS, TimeUnit.HOURS);
            return token;
        } catch (Exception e) {
            log.error("Failed to decrypt access token for tenant={}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    /**
     * Returns the Meta phone_number_id for the given tenant.
     * Used as the "from" identifier when sending messages.
     *
     * @param tenantId UUID string of the tenant.
     * @return phone_number_id string, or null if not configured.
     */
    public String getPhoneNumberId(String tenantId) {
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX_PHONE + tenantId);
        if (cached != null) return cached;

        Map<String, String> fetchedSettings = fetchSettings(tenantId);
        if (fetchedSettings == null) return null;

        String phoneNumberId = fetchedSettings.get("whatsapp_phone_number_id");
        if (phoneNumberId == null || phoneNumberId.isBlank()) return null;

        redisTemplate.opsForValue().set(
            CACHE_PREFIX_PHONE + tenantId, phoneNumberId, CACHE_TTL_HOURS, TimeUnit.HOURS);
        return phoneNumberId;
    }

    /**
     * Evicts the cached token and phone number ID for a tenant.
     * Called when a seller reconnects or disconnects their WhatsApp number.
     */
    public void evict(String tenantId) {
        redisTemplate.delete(CACHE_PREFIX_TOKEN + tenantId);
        redisTemplate.delete(CACHE_PREFIX_PHONE + tenantId);
        log.debug("Evicted WhatsApp token cache for tenant={}", tenantId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, String> fetchSettings(String tenantId) {
        try {
            // Call GET /api/v1/tenants/{slug}/detail — returns settings as a flat map.
            // We first need the slug from the tenant ID, but for simplicity we call
            // the by-whatsapp lookup if we already have phone_number_id, or
            // fall back to calling a settings-by-tenantId endpoint.
            // For now, use the tenant detail endpoint with the tenantId as-is
            // (the controller handles both slug and UUID in a future iteration).
            // TODO: add GET /api/v1/tenants/id/{uuid}/settings to tenant-service.
            Map<String, Object> response = (Map<String, Object>) webClientBuilder
                .baseUrl(tenantServiceUrl)
                .build()
                .get()
                .uri("/api/v1/tenants/settings/" + tenantId)
                .retrieve()
                .onStatus(status -> status.value() == 404, resp -> {
                    log.warn("No settings found for tenantId={}", tenantId);
                    return reactor.core.publisher.Mono.empty();
                })
                .bodyToMono(Map.class)
                .block();

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) return null;
            return (Map<String, String>) response.get("data");

        } catch (Exception e) {
            log.error("Failed to fetch settings for tenant={}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    /**
     * AES-256/ECB decryption of the stored access token.
     * The encryption key must be exactly 32 bytes (256 bits).
     * Tokens are encrypted by the tenant-service when stored via
     * the whatsapp/connect endpoint.
     *
     * Note: ECB mode is used here for simplicity since the input data
     * (a single token) has no block-pattern concerns. For multi-block
     * data, CBC with a random IV would be preferred.
     */
    private String decrypt(String encryptedBase64) throws Exception {
        byte[] keyBytes = encryptionKey.substring(0, 32).getBytes(StandardCharsets.UTF_8);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decoded    = Base64.getDecoder().decode(encryptedBase64);
        byte[] decrypted  = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}

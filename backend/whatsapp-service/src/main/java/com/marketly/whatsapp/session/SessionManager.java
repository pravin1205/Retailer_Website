package com.marketly.whatsapp.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Manages WhatsApp conversation sessions backed by Redis.
 *
 * Redis key pattern:
 *   wa:conv:{phoneNumberId}:{customerWaId}
 *
 * The TTL is refreshed on every message so active conversations stay alive.
 * Sessions that go idle for longer than the configured TTL expire automatically,
 * requiring the customer to restart the conversation on their next message.
 *
 * JSON serialisation is used (not Java serialisation) for Redis compatibility
 * across service restarts and potential future multi-instance deployments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionManager {

    private static final String KEY_PREFIX = "wa:conv:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Value("${app.conversation.session-ttl-minutes:30}")
    private long sessionTtlMinutes;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Retrieves an existing session, or creates a new UNVERIFIED one if none exists.
     * Always refreshes the TTL so active conversations do not expire mid-flow.
     *
     * @param phoneNumberId Meta's internal ID of the seller's WhatsApp number.
     * @param customerWaId  The customer's WhatsApp number in E.164 format.
     * @return The current or newly created session.
     */
    public ConversationSession getOrCreate(String phoneNumberId, String customerWaId) {
        String key = buildKey(phoneNumberId, customerWaId);
        String json = redisTemplate.opsForValue().get(key);

        if (json != null) {
            try {
                ConversationSession session = objectMapper.readValue(json, ConversationSession.class);
                session.setLastMessageAt(System.currentTimeMillis());
                save(session); // refresh TTL
                return session;
            } catch (JsonProcessingException e) {
                log.warn("Corrupt session JSON for key={}; creating fresh session. Error: {}",
                         key, e.getMessage());
            }
        }

        // No existing session — create a fresh UNVERIFIED one
        ConversationSession fresh = ConversationSession.builder()
            .phoneNumberId(phoneNumberId)
            .customerWaId(customerWaId)
            .state(ConversationState.UNVERIFIED)
            .language("en")
            .lastMessageAt(System.currentTimeMillis())
            .build();

        save(fresh);
        log.debug("Created new session for phoneNumberId={} customer={}", phoneNumberId, customerWaId);
        return fresh;
    }

    /**
     * Persists the session, resetting its TTL.
     * Call this after every state mutation to keep Redis in sync.
     */
    public void save(ConversationSession session) {
        try {
            String key  = buildKey(session.getPhoneNumberId(), session.getCustomerWaId());
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key, json, sessionTtlMinutes, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise session for phoneNumberId={} customer={}: {}",
                      session.getPhoneNumberId(), session.getCustomerWaId(), e.getMessage());
        }
    }

    /**
     * Deletes the session. Called when the customer sends STOP or after
     * completing an order and transitioning back to IDLE permanently.
     */
    public void delete(String phoneNumberId, String customerWaId) {
        String key = buildKey(phoneNumberId, customerWaId);
        redisTemplate.delete(key);
        log.debug("Deleted session for phoneNumberId={} customer={}", phoneNumberId, customerWaId);
    }

    /**
     * Returns the session if it exists, empty if it does not.
     * Does NOT create a new session — use getOrCreate() for that.
     */
    public Optional<ConversationSession> find(String phoneNumberId, String customerWaId) {
        String key  = buildKey(phoneNumberId, customerWaId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, ConversationSession.class));
        } catch (JsonProcessingException e) {
            log.warn("Could not deserialise session key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds the Redis key for a (phoneNumberId, customerWaId) pair.
     * Colons inside the values are safe because both parts are bounded by the separator.
     */
    private String buildKey(String phoneNumberId, String customerWaId) {
        return KEY_PREFIX + phoneNumberId + ":" + customerWaId;
    }
}

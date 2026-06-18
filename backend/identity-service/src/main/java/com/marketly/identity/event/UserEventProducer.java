package com.marketly.identity.event;

import com.marketly.identity.entity.User;
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
public class UserEventProducer {

    private static final String TOPIC_USER_REGISTERED  = "identity.user.registered";
    private static final String TOPIC_PASSWORD_CHANGED  = "identity.user.password-changed";
    private static final String TOPIC_OTP_REQUESTED     = "identity.user.otp-requested";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserRegistered(User user, String tenantSlug) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", TOPIC_USER_REGISTERED);
        payload.put("eventVersion", "1.0");
        payload.put("occurredAt", Instant.now().toString());
        payload.put("producedBy", "identity-service");
        // tenantSlug at root level so consumers (customer-service) can resolve tenantId
        payload.put("tenantSlug", tenantSlug != null ? tenantSlug : "");
        payload.put("payload", Map.of(
            "userId",     user.getId().toString(),
            "email",      user.getEmail(),
            "phone",      user.getPhone() != null ? user.getPhone() : "",
            "firstName",  "",   // populated during profile-completion step
            "lastName",   "",
            "tenantSlug", tenantSlug != null ? tenantSlug : ""
        ));

        kafkaTemplate.send(TOPIC_USER_REGISTERED, user.getId().toString(), payload)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish UserRegisteredEvent for user {}: {}",
                              user.getId(), ex.getMessage());
                } else {
                    log.debug("UserRegisteredEvent published for user {}", user.getId());
                }
            });
    }

    public void publishOtpRequested(String phone, String otpCode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", TOPIC_OTP_REQUESTED);
        payload.put("eventVersion", "1.0");
        payload.put("occurredAt", Instant.now().toString());
        payload.put("producedBy", "identity-service");
        payload.put("payload", Map.of(
            "phone",     phone,
            "otpCode",   otpCode,
            "channel",   "SMS",
            "expiresAt", Instant.now().plusSeconds(600).toString()
        ));

        kafkaTemplate.send(TOPIC_OTP_REQUESTED, phone, payload)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish OtpRequestedEvent for phone {}: {}", phone, ex.getMessage());
                } else {
                    log.debug("OtpRequestedEvent published for phone ending ...{}", phone.substring(Math.max(0, phone.length() - 4)));
                }
            });
    }

    public void publishPasswordChanged(User user, String ipAddress) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", TOPIC_PASSWORD_CHANGED);
        payload.put("eventVersion", "1.0");
        payload.put("occurredAt", Instant.now().toString());
        payload.put("producedBy", "identity-service");
        payload.put("payload", Map.of(
            "userId", user.getId().toString(),
            "email", user.getEmail(),
            "ipAddress", ipAddress != null ? ipAddress : "",
            "changedAt", Instant.now().toString()
        ));

        kafkaTemplate.send(TOPIC_PASSWORD_CHANGED, user.getId().toString(), payload)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish PasswordChangedEvent for user {}: {}",
                              user.getId(), ex.getMessage());
                }
            });
    }
}

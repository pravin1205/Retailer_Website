package com.marketly.notification.service;

import com.marketly.notification.channel.EmailChannel;
import com.marketly.notification.entity.Notification;
import com.marketly.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository  notificationRepository;
    private final EmailChannel            emailChannel;
    private final SimpMessagingTemplate   messagingTemplate;

    /**
     * Dispatch an OTP SMS — intentionally skips DB persistence because:
     * 1. OTPs are transient and must not be stored in notification history.
     * 2. The recipient has no userId yet at OTP-send time (pre-registration).
     *
     * In production, replace the log statement with a real SMS provider call.
     */
    public void dispatchOtp(String phone, String otpCode) {
        // Production: call SMS provider (Twilio / MSG91 / Kaleyra) here.
        // Dev: OTP is visible in the notification-service console log.
        log.info("[OTP-DEV] Code for ...{}: {}", phone.length() > 4 ? phone.substring(phone.length() - 4) : phone, otpCode);
        log.info("SMS not yet integrated — OTP logged above for development use");
    }

    /**
     * Central dispatch method.
     * Persists the notification first, then dispatches to channel(s).
     * recipientId MUST be non-null — use dispatchOtp() for pre-registration OTPs.
     */
    @Transactional
    public void dispatch(UUID tenantId, UUID recipientId, String recipientEmail,
                         String channel, String category, String title, String body,
                         UUID referenceId, String referenceType) {

        // Guard: recipientId is NOT NULL in the DB. Callers must supply a valid UUID.
        if (recipientId == null) {
            log.error("dispatch() called with null recipientId for channel={} category={}. Skipping persistence.", channel, category);
            return;
        }

        Notification notification = Notification.builder()
            .tenantId(tenantId)
            .recipientId(recipientId)
            .recipientEmail(recipientEmail)
            .channel(channel)
            .category(category)
            .title(title)
            .body(body)
            .referenceId(referenceId)
            .referenceType(referenceType)
            .build();

        notification = notificationRepository.save(notification);

        try {
            switch (channel) {
                case "EMAIL" -> sendEmail(notification);
                case "IN_APP" -> sendInApp(notification);
                case "PUSH"   -> log.info("PUSH not yet integrated — skipping for {}", recipientId);
                case "SMS"    -> log.info("SMS not yet integrated — skipping for {}", recipientId);
                default       -> log.warn("Unknown notification channel: {}", channel);
            }
            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            notification.setStatus("FAILED");
            notification.setFailureReason(e.getMessage());
            log.error("Notification dispatch failed [{}/{}]: {}", channel, category, e.getMessage());
        }

        notificationRepository.save(notification);
    }

    /** Convenience: dispatch to both EMAIL and IN_APP */
    public void dispatchAll(UUID tenantId, UUID recipientId, String recipientEmail,
                            String category, String title, String body,
                            UUID referenceId, String referenceType) {
        dispatch(tenantId, recipientId, recipientEmail,
                 "EMAIL", category, title, body, referenceId, referenceType);
        dispatch(tenantId, recipientId, recipientEmail,
                 "IN_APP", category, title, body, referenceId, referenceType);
    }

    @Transactional(readOnly = true)
    public Page<Notification> listForUser(UUID recipientId, int page, int size) {
        return notificationRepository
            .findByRecipientIdOrderByCreatedAtDesc(recipientId, PageRequest.of(page - 1, size));
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID recipientId) {
        return notificationRepository
            .countByRecipientIdAndStatusAndChannel(recipientId, "SENT", "IN_APP");
    }

    @Transactional
    public void markAllRead(UUID recipientId) {
        notificationRepository.markAllReadForUser(recipientId);
    }

    // ── Channel adapters ─────────────────────────────────────────────────

    private void sendEmail(Notification n) {
        if (n.getRecipientEmail() == null || n.getRecipientEmail().isBlank()) {
            log.warn("No email address for notification {}", n.getId());
            return;
        }
        emailChannel.send(n.getRecipientEmail(), n.getTitle(), n.getBody());
    }

    private void sendInApp(Notification n) {
        // Push to customer's personal WebSocket topic
        String customerTopic = String.format("/topic/users/%s", n.getRecipientId());
        messagingTemplate.convertAndSend(customerTopic, Map.of(
            "id",            n.getId(),
            "category",      n.getCategory(),
            "title",         n.getTitle(),
            "body",          n.getBody(),
            "referenceId",   n.getReferenceId() != null ? n.getReferenceId().toString() : "",
            "referenceType", n.getReferenceType() != null ? n.getReferenceType() : "",
            "createdAt",     n.getCreatedAt() != null ? n.getCreatedAt().toString() : ""
        ));

        // Also push to tenant's store-owner dashboard if it's a store alert
        if (n.getTenantId() != null && (
            "LOW_STOCK".equals(n.getCategory()) ||
            "ORDER_UPDATE".equals(n.getCategory()))) {
            String storeTopic = String.format("/topic/store/%s", n.getTenantId());
            messagingTemplate.convertAndSend(storeTopic, Map.of(
                "category", n.getCategory(),
                "title",    n.getTitle(),
                "body",     n.getBody()
            ));
        }
    }
}

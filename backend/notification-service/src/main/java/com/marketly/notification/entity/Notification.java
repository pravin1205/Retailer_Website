package com.marketly.notification.entity;

import com.marketly.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends AuditableEntity {

    /** NULL = platform-wide; non-null = tenant-scoped */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    /**
     * Delivery channel: EMAIL | SMS | PUSH | IN_APP
     */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    /**
     * Notification domain: ORDER_UPDATE | LOW_STOCK | PROMOTION |
     *                       WELCOME | SECURITY | SYSTEM
     */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    /**
     * PENDING → SENT | FAILED
     * SENT → READ (for IN_APP)
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    /** orderId, productId, etc. */
    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}

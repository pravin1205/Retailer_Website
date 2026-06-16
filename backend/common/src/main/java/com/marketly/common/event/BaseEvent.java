package com.marketly.common.event;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event envelope — all published events extend this.
 * Every event carries: eventId (idempotency), type, tenantId, correlationId.
 */
@Getter
public abstract class BaseEvent {

    private final String eventId;
    private final String eventType;
    private final String eventVersion;
    private final Instant occurredAt;
    private final UUID tenantId;
    private final String correlationId;
    private final String producedBy;

    protected BaseEvent(String eventType, String eventVersion, UUID tenantId,
                        String correlationId, String producedBy) {
        this.eventId       = UUID.randomUUID().toString();
        this.eventType     = eventType;
        this.eventVersion  = eventVersion;
        this.occurredAt    = Instant.now();
        this.tenantId      = tenantId;
        this.correlationId = correlationId;
        this.producedBy    = producedBy;
    }
}

package com.marketly.tenant.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Response returned by the WhatsApp connection status and connect endpoints.
 * Consumed by the seller dashboard to render the current connection state.
 */
@Value
@Builder
public class WhatsappStatusResponse {

    /** Whether a WhatsApp number is currently connected for this tenant. */
    boolean connected;

    /**
     * Human-readable phone number connected.
     * Null when {@code connected} is false.
     */
    String displayNumber;

    /**
     * ISO-8601 timestamp of when the number was connected.
     * Null when {@code connected} is false.
     */
    String connectedAt;
}

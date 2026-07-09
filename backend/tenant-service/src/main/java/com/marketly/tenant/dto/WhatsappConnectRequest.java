package com.marketly.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body sent by the seller dashboard after the seller completes
 * Meta's Embedded Signup flow and grants Marketly permission to use
 * their WhatsApp Business phone number.
 *
 * The frontend receives all three values from Meta's JavaScript SDK callback
 * and forwards them here. The backend exchanges the {@code authCode} for a
 * permanent access token via the Meta Graph API and stores the result
 * encrypted in tenant_settings.
 */
@Data
public class WhatsappConnectRequest {

    /**
     * Meta's internal identifier for the WhatsApp phone number.
     * Used in every Graph API call to send messages from this number.
     * Example: "123456789012345"
     */
    @NotBlank(message = "phone_number_id from Meta is required")
    private String phoneNumberId;

    /**
     * Human-readable phone number shown in the dashboard.
     * Example: "+919876543210"
     */
    @NotBlank(message = "display_number is required")
    private String displayNumber;

    /**
     * Short-lived authorisation code returned by Meta's Embedded Signup SDK.
     * The tenant-service exchanges this code for a permanent system user
     * access token via POST https://graph.facebook.com/v19.0/oauth/access_token.
     * The permanent token is AES-256 encrypted before being stored in
     * tenant_settings as {@code whatsapp_access_token}.
     */
    @NotBlank(message = "auth_code from Meta Embedded Signup is required")
    private String authCode;
}

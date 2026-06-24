package com.marketly.whatsapp.handler;

import com.marketly.whatsapp.meta.MetaApiClient;
import com.marketly.whatsapp.session.ConversationSession;
import com.marketly.whatsapp.session.ConversationState;
import com.marketly.whatsapp.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Handles the OTP-based account linking flow for new WhatsApp customers.
 *
 * First-time flow:
 *  1. Customer sends any message to the seller's WhatsApp number.
 *  2. AuthHandler sends a welcome message and triggers an OTP via identity-service.
 *  3. Session state transitions to AWAITING_OTP.
 *  4. Customer replies with the 6-digit OTP.
 *  5. AuthHandler calls identity-service to verify the OTP.
 *  6. On success: customer's JWT and userId are stored in the session.
 *     Session state transitions to IDLE. The handler sends a greeting.
 *  7. On failure: customer gets an error message and can retry.
 *
 * Returning customer flow:
 *  - The session is already IDLE (or a higher state) with an access token.
 *  - AuthHandler is not involved; MessageRouter routes directly to the
 *    appropriate state handler.
 *
 * This handler reuses the existing identity-service OTP API — the same
 * endpoints the web frontend uses. No new backend code is required for auth.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthHandler {

    private final SessionManager  sessionManager;
    private final MetaApiClient   metaApiClient;
    private final WebClient.Builder webClientBuilder;

    @Value("${app.services.identity-service-url:http://localhost:8081}")
    private String identityServiceUrl;

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Handles a message for a session that is in UNVERIFIED or AWAITING_OTP state.
     *
     * @param session The current conversation session.
     * @param message Raw inbound message from Meta webhook.
     */
    public void handle(ConversationSession session, Map<String, Object> message) {
        ConversationState state = session.getState();

        if (state == ConversationState.UNVERIFIED) {
            initiateVerification(session);
        } else if (state == ConversationState.AWAITING_OTP) {
            verifyOtp(session, message);
        }
    }

    // ── Initiate verification ─────────────────────────────────────────────────

    /**
     * Sends a welcome message and triggers OTP delivery via identity-service.
     * Transitions session to AWAITING_OTP.
     */
    private void initiateVerification(ConversationSession session) {
        String phoneNumberId = session.getPhoneNumberId();
        String customerWaId  = session.getCustomerWaId();

        // The customer's WhatsApp ID IS their phone number in E.164 without the "+"
        // e.g. "919876543210" → phone for OTP = "9876543210" (strip country code)
        String phone = normalisePhone(customerWaId);

        try {
            // Call identity-service to generate and send OTP
            triggerOtp(phone);

            String name = session.getCustomerName() != null
                ? session.getCustomerName() : "there";

            metaApiClient.sendText(phoneNumberId, getSellerAccessToken(session), customerWaId,
                "👋 Welcome to " + getStoreName(session) + " on WhatsApp!\n\n" +
                "To place orders, we need to verify your number.\n" +
                "An OTP has been sent to +" + customerWaId + ".\n\n" +
                "Please reply with the 6-digit OTP to continue.");

            // Transition to AWAITING_OTP
            session.setState(ConversationState.AWAITING_OTP);
            sessionManager.save(session);

            log.info("OTP initiated for phone={} tenant={}", phone, session.getTenantId());

        } catch (Exception e) {
            log.error("Failed to initiate OTP for phone={}: {}", phone, e.getMessage());
            metaApiClient.sendText(phoneNumberId, getSellerAccessToken(session), customerWaId,
                "Sorry, we couldn't send an OTP right now. Please try again in a moment.");
        }
    }

    // ── Verify OTP ────────────────────────────────────────────────────────────

    /**
     * Verifies the OTP the customer replied with.
     * On success: stores JWT in session, transitions to IDLE.
     * On failure: sends error message, stays in AWAITING_OTP.
     */
    private void verifyOtp(ConversationSession session, Map<String, Object> message) {
        String phoneNumberId = session.getPhoneNumberId();
        String customerWaId  = session.getCustomerWaId();

        // Extract OTP text from the message
        String otpInput = extractTextBody(message);
        if (otpInput == null || otpInput.isBlank()) {
            metaApiClient.sendText(phoneNumberId, getSellerAccessToken(session), customerWaId,
                "Please reply with the 6-digit OTP we sent you.");
            return;
        }

        // Strip non-digits — customer might type "My OTP is 847291"
        String otp   = otpInput.replaceAll("[^0-9]", "");
        String phone = normalisePhone(customerWaId);

        if (otp.length() != 6) {
            metaApiClient.sendText(phoneNumberId, getSellerAccessToken(session), customerWaId,
                "That doesn't look like a 6-digit OTP. Please check and try again.");
            return;
        }

        try {
            Map<String, Object> verifyResult = callVerifyOtp(phone, otp, session.getTenantSlug());

            // The identity-service OTP verify returns { accessToken, refreshToken, user }
            // inside the data envelope. If accessToken is missing, the OTP was wrong.
            String accessToken = (String) verifyResult.get("accessToken");
            if (accessToken == null || accessToken.isBlank()) {
                metaApiClient.sendText(phoneNumberId, getSellerAccessToken(session), customerWaId,
                    "❌ Incorrect OTP. Please try again or request a new one by sending any message.");
                return;
            }

            // ── Verification successful ────────────────────────────────────────
            @SuppressWarnings("unchecked")
            Map<String, Object> userData = (Map<String, Object>) verifyResult.get("user");

            session.setAccessToken(accessToken);
            if (userData != null && userData.get("id") != null) {
                session.setCustomerId(java.util.UUID.fromString(userData.get("id").toString()));
            }
            session.setState(ConversationState.IDLE);
            sessionManager.save(session);

            String name = session.getCustomerName() != null
                ? session.getCustomerName() : "";
            String greeting = name.isBlank()
                ? "Welcome! ✅" : "Welcome, " + name + "! ✅";

            metaApiClient.sendText(phoneNumberId, accessToken, customerWaId,
                greeting + "\n\nYou're all set. What would you like to order today?\n\n" +
                "Just tell me — for example:\n" +
                "• \"2 litres of milk\"\n" +
                "• \"1 dozen eggs and a loaf of bread\"\n\n" +
                "Or send a voice note in Hindi, Tamil, Telugu, Kannada, or English! 🎙");

            log.info("OTP verified for phone={} tenant={} customerId={}",
                     phone, session.getTenantId(), session.getCustomerId());

        } catch (Exception e) {
            log.error("OTP verification failed for phone={}: {}", phone, e.getMessage());
            metaApiClient.sendText(phoneNumberId, getSellerAccessToken(session), customerWaId,
                "Something went wrong. Please try sending any message to request a new OTP.");
            // Reset to UNVERIFIED so the next message retriggers OTP
            session.setState(ConversationState.UNVERIFIED);
            sessionManager.save(session);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Calls identity-service to generate and dispatch an OTP.
     * POST /api/v1/auth/otp/send { "phone": "..." }
     */
    private void triggerOtp(String phone) {
        webClientBuilder
            .baseUrl(identityServiceUrl)
            .build()
            .post()
            .uri("/api/v1/auth/otp/send")
            .bodyValue(Map.of("phone", phone))
            .retrieve()
            .bodyToMono(Map.class)
            .block();
    }

    /**
     * Calls identity-service to verify an OTP and obtain a JWT.
     * POST /api/v1/auth/otp/verify { phone, otp, tenantSlug }
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callVerifyOtp(String phone, String otp, String tenantSlug) {
        Map<String, Object> responseEnvelope = (Map<String, Object>) webClientBuilder
            .baseUrl(identityServiceUrl)
            .build()
            .post()
            .uri("/api/v1/auth/otp/verify")
            .bodyValue(Map.of("phone", phone, "otp", otp, "tenantSlug", tenantSlug != null ? tenantSlug : ""))
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (responseEnvelope == null || !Boolean.TRUE.equals(responseEnvelope.get("success"))) {
            throw new IllegalStateException("OTP verification failed");
        }
        return (Map<String, Object>) responseEnvelope.get("data");
    }

    /** Strips the country code prefix to get the 10-digit phone number for OTP. */
    private String normalisePhone(String waId) {
        // WhatsApp IDs are E.164 without "+": "919876543210"
        // Indian numbers start with 91 (2 digits country code)
        if (waId.startsWith("91") && waId.length() == 12) {
            return waId.substring(2);
        }
        return waId; // fallback — return as-is for non-Indian numbers
    }

    private String extractTextBody(Map<String, Object> message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> text = (Map<String, Object>) message.get("text");
        return text != null ? (String) text.get("body") : null;
    }

    /**
     * Returns the seller's system access token for sending messages.
     * Before the customer is verified, we need the seller's own token.
     *
     * TODO: in Phase 3 load the actual seller access token from tenant_settings
     * (decrypted via the encryption key). For now, returns the customer token if
     * available, or empty string as a safe default.
     */
    private String getSellerAccessToken(ConversationSession session) {
        return session.getAccessToken() != null ? session.getAccessToken() : "";
    }

    private String getStoreName(ConversationSession session) {
        return session.getTenantSlug() != null ? session.getTenantSlug() : "this store";
    }
}

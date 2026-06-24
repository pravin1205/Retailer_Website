package com.marketly.whatsapp.controller;

import com.marketly.whatsapp.handler.PaymentHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Receives Razorpay payment webhook events and triggers order placement
 * for WhatsApp orders that were awaiting UPI payment.
 *
 * Endpoint: POST /whatsapp/payment/webhook
 * (Whitelisted in JwtAuthFilter — no JWT required; authenticated via Razorpay signature)
 *
 * Razorpay fires this when a payment link is paid:
 *   event: "payment_link.paid"
 *   payload.payment_link.entity.id       — the payment link ID
 *   payload.payment.entity.id            — the Razorpay payment ID
 *   payload.payment.entity.notes.phoneNumberId  — seller's WA phone_number_id
 *   payload.payment.entity.notes.customerWaId   — customer's WhatsApp number
 *
 * Security:
 *   Razorpay sends X-Razorpay-Signature: HMAC-SHA256(webhookSecret, rawBody)
 *   We validate this before processing any payment.
 */
@RestController
@RequestMapping("/whatsapp/payment/webhook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WhatsApp Payment Webhook", description = "Razorpay payment event receiver for WhatsApp orders")
public class PaymentWebhookController {

    private final PaymentHandler paymentHandler;

    @Value("${app.razorpay.webhook-secret:}")
    private String razorpayWebhookSecret;

    @PostMapping
    @Operation(summary = "Handle Razorpay payment_link.paid webhook for WhatsApp orders")
    public ResponseEntity<Void> handlePaymentWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "Content-Type", required = false) String contentType) {

        // Validate Razorpay signature if webhook secret is configured
        if (razorpayWebhookSecret != null && !razorpayWebhookSecret.isBlank()) {
            if (!isValidSignature(payload, signature)) {
                log.warn("PaymentWebhook: invalid X-Razorpay-Signature — rejecting");
                return ResponseEntity.status(401).build();
            }
        }

        String event = (String) payload.get("event");
        if (!"payment_link.paid".equals(event)) {
            // Acknowledge non-payment events without processing
            log.debug("PaymentWebhook: ignoring event={}", event);
            return ResponseEntity.ok().build();
        }

        try {
            String phoneNumberId = extractPhoneNumberId(payload);
            String customerWaId  = extractCustomerWaId(payload);
            String paymentId     = extractPaymentId(payload);

            if (phoneNumberId == null || customerWaId == null) {
                log.warn("PaymentWebhook: missing phoneNumberId or customerWaId in payment notes — " +
                         "cannot route to session. Payload: {}", payload);
                return ResponseEntity.ok().build();
            }

            log.info("PaymentWebhook: payment_link.paid for phoneNumberId={} customer={} paymentId={}",
                     phoneNumberId, customerWaId, paymentId);

            // Delegate to PaymentHandler which looks up the session and places the order
            paymentHandler.handlePaymentSuccess(phoneNumberId, customerWaId, paymentId);

        } catch (Exception e) {
            log.error("PaymentWebhook: error processing payment event: {}", e.getMessage(), e);
            // Return 200 so Razorpay doesn't retry — we've logged the failure
        }

        return ResponseEntity.ok().build();
    }

    // ── Payload extraction ────────────────────────────────────────────────────

    /**
     * Extracts the seller's WhatsApp phone_number_id from the payment notes.
     *
     * When creating a Razorpay payment link, we store the phoneNumberId in the
     * notes so we can route the webhook back to the correct seller's session.
     *
     * Note path: payload.payload.payment.entity.notes.phoneNumberId
     */
    @SuppressWarnings("unchecked")
    private String extractPhoneNumberId(Map<String, Object> event) {
        try {
            Map<String, Object> innerPayload = (Map<String, Object>) event.get("payload");
            Map<String, Object> payment      = (Map<String, Object>) innerPayload.get("payment");
            Map<String, Object> entity       = (Map<String, Object>) payment.get("entity");
            Map<String, Object> notes        = (Map<String, Object>) entity.get("notes");
            return notes != null ? (String) notes.get("phoneNumberId") : null;
        } catch (Exception e) {
            log.debug("Could not extract phoneNumberId from webhook payload: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the customer's WhatsApp ID from payment notes.
     * Note path: payload.payload.payment.entity.notes.customerWaId
     */
    @SuppressWarnings("unchecked")
    private String extractCustomerWaId(Map<String, Object> event) {
        try {
            Map<String, Object> innerPayload = (Map<String, Object>) event.get("payload");
            Map<String, Object> payment      = (Map<String, Object>) innerPayload.get("payment");
            Map<String, Object> entity       = (Map<String, Object>) payment.get("entity");
            Map<String, Object> notes        = (Map<String, Object>) entity.get("notes");
            return notes != null ? (String) notes.get("customerWaId") : null;
        } catch (Exception e) {
            log.debug("Could not extract customerWaId from webhook payload: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the Razorpay payment ID for the transaction record.
     * Note path: payload.payload.payment.entity.id
     */
    @SuppressWarnings("unchecked")
    private String extractPaymentId(Map<String, Object> event) {
        try {
            Map<String, Object> innerPayload = (Map<String, Object>) event.get("payload");
            Map<String, Object> payment      = (Map<String, Object>) innerPayload.get("payment");
            Map<String, Object> entity       = (Map<String, Object>) payment.get("entity");
            return entity != null ? (String) entity.get("id") : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Signature validation ──────────────────────────────────────────────────

    /**
     * Validates the HMAC-SHA256 signature Razorpay attaches to every webhook POST.
     *
     * Razorpay computes: HMAC-SHA256(webhookSecret, rawBody)
     * We recompute this and compare. Without this check, any attacker who
     * knows the webhook URL could trigger fake order placements.
     */
    private boolean isValidSignature(Map<String, Object> payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) return false;
        try {
            String body = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(payload);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                razorpayWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));

            return signatureHeader.equals(sb.toString());
        } catch (Exception e) {
            log.error("PaymentWebhook: signature validation error: {}", e.getMessage());
            return false;
        }
    }
}

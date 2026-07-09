package com.marketly.whatsapp.controller;

import com.marketly.whatsapp.handler.MessageRouter;
import com.marketly.whatsapp.service.TenantResolver;
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
import java.util.List;
import java.util.Map;

/**
 * Receives all inbound WhatsApp messages from Meta's Cloud API.
 *
 * Two endpoints:
 *
 *  GET  /whatsapp/webhook  — Meta's one-time webhook verification challenge.
 *                            Must respond with hub.challenge within 5 seconds.
 *
 *  POST /whatsapp/webhook  — All inbound message events (text, audio, interactive
 *                            button replies, delivery receipts, etc.).
 *                            Must return HTTP 200 within 5 seconds; all heavy
 *                            processing is handed off to MessageRouter asynchronously.
 *
 * Security:
 *  - GET:  Verified by comparing hub.verify_token to the configured secret.
 *  - POST: Verified by checking X-Hub-Signature-256 (HMAC-SHA256 of the raw
 *          request body using the Meta App Secret).
 *
 * Both endpoints are whitelisted in JwtAuthFilter — they must be publicly
 * reachable by Meta's servers.
 */
@RestController
@RequestMapping("/whatsapp/webhook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WhatsApp Webhook", description = "Meta Cloud API webhook receiver")
public class WebhookController {

    private final MessageRouter   messageRouter;
    private final TenantResolver  tenantResolver;

    @Value("${app.meta.verify-token:dev-verify-token}")
    private String verifyToken;

    @Value("${app.meta.app-secret:}")
    private String appSecret;

    // ── GET /whatsapp/webhook — verification challenge ─────────────────────

    /**
     * Meta calls this endpoint once during webhook registration.
     * If the verify_token matches, we echo back hub.challenge.
     * Failure to respond correctly prevents webhook activation.
     */
    @GetMapping
    @Operation(summary = "Meta webhook verification challenge")
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode")         String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge")    String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("Meta webhook verification successful");
            return ResponseEntity.ok(challenge);
        }

        log.warn("Meta webhook verification failed — mode={} token_match={}",
                 mode, verifyToken.equals(token));
        return ResponseEntity.status(403).body("Forbidden");
    }

    // ── POST /whatsapp/webhook — inbound messages ──────────────────────────

    /**
     * Handles all inbound WhatsApp events from Meta.
     *
     * Design principle: this method MUST return HTTP 200 within 5 seconds.
     * Meta retries delivery if it does not receive a 200. If we return 200
     * on every valid webhook (even for messages we cannot process), Meta
     * stops retrying and moves on.
     *
     * All actual message processing is delegated to MessageRouter which runs
     * on a separate async executor. This endpoint is never blocked by NLP,
     * STT, or downstream API calls.
     */
    @PostMapping
    @Operation(summary = "Receive inbound WhatsApp messages")
    public ResponseEntity<Void> receive(
            @RequestBody  Map<String, Object> payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "Content-Type", required = false) String contentType) {

        // Signature validation — skip only in dev if appSecret is not configured
        if (appSecret != null && !appSecret.isBlank()) {
            if (!isValidSignature(payload, signature)) {
                log.warn("Meta webhook: invalid X-Hub-Signature-256 — rejecting");
                return ResponseEntity.status(401).build();
            }
        }

        // Validate it is a WhatsApp Business Account webhook
        if (!"whatsapp_business_account".equals(payload.get("object"))) {
            log.debug("Ignoring non-WABA webhook object={}", payload.get("object"));
            return ResponseEntity.ok().build();
        }

        // Extract and dispatch each change entry
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) payload.get("entry");
        if (entries == null) return ResponseEntity.ok().build();

        for (Map<String, Object> entry : entries) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
            if (changes == null) continue;

            for (Map<String, Object> change : changes) {
                @SuppressWarnings("unchecked")
                Map<String, Object> value = (Map<String, Object>) change.get("value");
                if (value == null) continue;

                dispatchChange(value);
            }
        }

        // Always return 200 immediately — processing is async
        return ResponseEntity.ok().build();
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Dispatches a single change value to MessageRouter.
     * Each "change" contains metadata (phone_number_id) and optionally
     * a list of messages, statuses, or errors.
     */
    @SuppressWarnings("unchecked")
    private void dispatchChange(Map<String, Object> value) {
        Map<String, Object> metadata = (Map<String, Object>) value.get("metadata");
        if (metadata == null) return;

        String phoneNumberId = (String) metadata.get("phone_number_id");
        if (phoneNumberId == null || phoneNumberId.isBlank()) return;

        List<Map<String, Object>> messages = (List<Map<String, Object>>) value.get("messages");
        if (messages == null || messages.isEmpty()) {
            // This is a delivery receipt or read receipt — no action needed
            return;
        }

        List<Map<String, Object>> contacts = (List<Map<String, Object>>) value.get("contacts");

        for (Map<String, Object> message : messages) {
            String customerWaId = (String) message.get("from");
            if (customerWaId == null) continue;

            // Resolve customer name from contacts if available
            String customerName = resolveContactName(contacts, customerWaId);

            // Resolve tenant — if not found, silently discard
            // (the number may have been disconnected)
            try {
                tenantResolver.resolve(phoneNumberId);
            } catch (TenantResolver.TenantNotFoundException e) {
                log.warn("Received message on unregistered phoneNumberId={} — discarding", phoneNumberId);
                return;
            }

            // Hand off to async message router — returns immediately
            messageRouter.route(phoneNumberId, customerWaId, customerName, message);
        }
    }

    /** Extracts the customer's display name from the contacts block if present. */
    @SuppressWarnings("unchecked")
    private String resolveContactName(List<Map<String, Object>> contacts, String waId) {
        if (contacts == null) return null;
        return contacts.stream()
            .filter(c -> waId.equals(c.get("wa_id")))
            .map(c -> {
                Map<String, Object> profile = (Map<String, Object>) c.get("profile");
                return profile != null ? (String) profile.get("name") : null;
            })
            .findFirst()
            .orElse(null);
    }

    /**
     * Validates the HMAC-SHA256 signature Meta attaches to every webhook POST.
     *
     * Meta computes: HMAC-SHA256(appSecret, rawRequestBody)
     * and sends it as: X-Hub-Signature-256: sha256={hex}
     *
     * We must verify this to ensure the webhook is from Meta and has not
     * been tampered with. Without this check, any attacker who knows our
     * webhook URL could inject fake orders.
     *
     * NOTE: In a production deployment, this check should operate on the raw
     * request bytes BEFORE Spring parses the JSON. Here we re-serialise to
     * approximate it — a proper implementation would use a Filter to capture
     * the raw body bytes and store them as a request attribute.
     */
    private boolean isValidSignature(Map<String, Object> payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            String expected = signatureHeader.substring(7); // strip "sha256="
            String body = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(payload);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));

            return expected.equals(sb.toString());
        } catch (Exception e) {
            log.error("Signature validation error: {}", e.getMessage());
            return false;
        }
    }
}

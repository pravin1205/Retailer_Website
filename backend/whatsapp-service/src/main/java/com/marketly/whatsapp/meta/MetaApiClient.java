package com.marketly.whatsapp.meta;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the Meta WhatsApp Cloud API (Graph API v19.0).
 *
 * All outbound messages are sent FROM the seller's specific phone number using
 * the phone_number_id. This is what preserves seller isolation — each store's
 * customers see messages from their store's number only.
 *
 * Supported message types:
 *  - Text              — plain-text messages
 *  - Interactive       — button messages (up to 3 reply buttons)
 *  - Interactive list  — scrollable list of options (e.g. address selection)
 *  - CTA URL           — button that opens a URL (e.g. payment link)
 *
 * Media:
 *  - downloadMedia()   — downloads voice note / image from Meta media servers
 *
 * All send operations are fire-and-forget (subscribe without blocking). If
 * a send fails, the error is logged but does not propagate to the caller —
 * the customer conversation continues and the next message will retry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetaApiClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.meta.graph-api-url:https://graph.facebook.com/v19.0}")
    private String graphApiUrl;

    // ── Text message ──────────────────────────────────────────────────────────

    /**
     * Sends a plain-text message to a customer from the seller's number.
     *
     * @param phoneNumberId Meta's internal ID of the seller's WhatsApp number.
     * @param accessToken   Permanent access token for this seller's WABA.
     * @param toWaId        Customer's WhatsApp number in E.164 (e.g. "919876543210").
     * @param text          Message body. Supports WhatsApp markdown (*bold*, _italic_).
     */
    public void sendText(String phoneNumberId, String accessToken,
                         String toWaId, String text) {
        Map<String, Object> body = Map.of(
            "messaging_product", "whatsapp",
            "recipient_type",    "individual",
            "to",                toWaId,
            "type",              "text",
            "text",              Map.of("preview_url", false, "body", text)
        );
        sendMessage(phoneNumberId, accessToken, body);
    }

    // ── Interactive button message ─────────────────────────────────────────────

    /**
     * Sends an interactive message with up to 3 reply buttons.
     *
     * Example usage: "PAY / EDIT / CANCEL" order confirmation.
     *
     * @param phoneNumberId Meta's internal ID of the seller's WhatsApp number.
     * @param accessToken   Permanent access token for this WABA.
     * @param toWaId        Customer's WhatsApp number.
     * @param bodyText      Message body shown above the buttons.
     * @param buttons       List of button labels. Max 3. Each becomes a reply button
     *                      whose id and title are both set to the label value.
     */
    public void sendButtons(String phoneNumberId, String accessToken,
                            String toWaId, String bodyText, List<String> buttons) {
        if (buttons == null || buttons.isEmpty() || buttons.size() > 3) {
            throw new IllegalArgumentException("Interactive buttons: 1–3 buttons required");
        }

        List<Map<String, Object>> buttonList = buttons.stream()
            .map(label -> Map.<String, Object>of(
                "type",  "reply",
                "reply", Map.of("id", label.toUpperCase().replace(" ", "_"), "title", label)
            ))
            .toList();

        Map<String, Object> body = Map.of(
            "messaging_product", "whatsapp",
            "recipient_type",    "individual",
            "to",                toWaId,
            "type",              "interactive",
            "interactive", Map.of(
                "type",   "button",
                "body",   Map.of("text", bodyText),
                "action", Map.of("buttons", buttonList)
            )
        );
        sendMessage(phoneNumberId, accessToken, body);
    }

    // ── Interactive list message ───────────────────────────────────────────────

    /**
     * Sends an interactive list message — a scrollable list the customer can
     * tap to select from. Used for address selection and slot selection.
     *
     * @param phoneNumberId  Meta's internal ID of the seller's WhatsApp number.
     * @param accessToken    Permanent access token for this WABA.
     * @param toWaId         Customer's WhatsApp number.
     * @param bodyText       Message body shown above the list.
     * @param buttonLabel    Label on the button that opens the list (max 20 chars).
     * @param sections       List of sections. Each section has a "title" (String)
     *                       and "rows" (List of {id, title, description?}).
     */
    public void sendList(String phoneNumberId, String accessToken,
                         String toWaId, String bodyText,
                         String buttonLabel, List<Map<String, Object>> sections) {
        Map<String, Object> body = Map.of(
            "messaging_product", "whatsapp",
            "recipient_type",    "individual",
            "to",                toWaId,
            "type",              "interactive",
            "interactive", Map.of(
                "type",   "list",
                "body",   Map.of("text", bodyText),
                "action", Map.of("button", buttonLabel, "sections", sections)
            )
        );
        sendMessage(phoneNumberId, accessToken, body);
    }

    // ── CTA URL button ────────────────────────────────────────────────────────

    /**
     * Sends a message with a single call-to-action button that opens a URL.
     * Used to send Razorpay payment links in-chat.
     *
     * @param phoneNumberId Meta's internal ID of the seller's WhatsApp number.
     * @param accessToken   Permanent access token for this WABA.
     * @param toWaId        Customer's WhatsApp number.
     * @param bodyText      Message body shown above the button.
     * @param buttonText    Label on the button (e.g. "Pay ₹489").
     * @param url           The URL the button opens (e.g. Razorpay payment link).
     */
    public void sendCtaUrl(String phoneNumberId, String accessToken,
                           String toWaId, String bodyText,
                           String buttonText, String url) {
        Map<String, Object> body = Map.of(
            "messaging_product", "whatsapp",
            "recipient_type",    "individual",
            "to",                toWaId,
            "type",              "interactive",
            "interactive", Map.of(
                "type",   "cta_url",
                "body",   Map.of("text", bodyText),
                "action", Map.of(
                    "name",       "cta_url",
                    "parameters", Map.of("display_text", buttonText, "url", url)
                )
            )
        );
        sendMessage(phoneNumberId, accessToken, body);
    }

    // ── Media download ────────────────────────────────────────────────────────

    /**
     * Downloads a media file (voice note, image) from Meta's servers.
     *
     * Flow:
     *  1. GET /v19.0/{mediaId} → returns { url, mime_type, file_size }
     *  2. GET {url} with Authorization header → returns raw file bytes
     *
     * @param mediaId     Meta's internal media identifier from the webhook payload.
     * @param accessToken The access token for the WABA that received the media.
     * @return Raw file bytes (OGG for voice notes, JPEG/PNG for images).
     */
    public byte[] downloadMedia(String mediaId, String accessToken) {
        WebClient client = webClientBuilder
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .build();

        // Step 1 — resolve media URL
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) client
            .get()
            .uri(graphApiUrl + "/" + mediaId)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (meta == null || meta.get("url") == null) {
            throw new IllegalStateException("Meta did not return a download URL for mediaId=" + mediaId);
        }

        String downloadUrl = meta.get("url").toString();

        // Step 2 — download the media bytes
        DataBuffer buffer = client
            .get()
            .uri(downloadUrl)
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .retrieve()
            .bodyToMono(DataBuffer.class)
            .block();

        if (buffer == null) {
            throw new IllegalStateException("Empty media download for mediaId=" + mediaId);
        }

        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        log.debug("Downloaded {}B media for mediaId={}", bytes.length, mediaId);
        return bytes;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Sends a message payload to the Meta Graph API.
     * Fire-and-forget: errors are logged but not propagated.
     */
    private void sendMessage(String phoneNumberId, String accessToken,
                             Map<String, Object> payload) {
        String uri = graphApiUrl + "/" + phoneNumberId + "/messages";

        webClientBuilder
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
            .post()
            .uri(uri)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(Map.class)
            .doOnError(e -> log.error("Meta API send failed for phoneNumberId={}: {}",
                                      phoneNumberId, e.getMessage()))
            .onErrorResume(e -> Mono.empty())   // suppress — do not crash the handler
            .subscribe(response ->
                log.debug("Meta API response for phoneNumberId={}: {}", phoneNumberId, response));
    }
}

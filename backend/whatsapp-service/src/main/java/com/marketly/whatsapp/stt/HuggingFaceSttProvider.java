package com.marketly.whatsapp.stt;

import com.marketly.whatsapp.nlp.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * STT provider backed by HuggingFace Inference API (Whisper large-v3).
 *
 * Priority: 3 (last-resort fallback)
 *
 * Characteristics:
 *  - Free tier: ~10 minutes of audio/day on the Inference API
 *  - Model: openai/whisper-large-v3
 *  - Cold start on free tier: up to 30 seconds (model loading)
 *  - Warm latency: 3–8 seconds per voice note
 *  - No language detection in response — defaults to "en"
 *
 * API differences from OpenAI / Groq:
 *  - Endpoint: https://api-inference.huggingface.co/models/openai/whisper-large-v3
 *  - Request body: raw audio bytes (NOT multipart form-data)
 *  - Content-Type: audio/ogg (matches WhatsApp voice note format)
 *  - Response: { "text": "transcription" }
 *  - No language detection field in response — always returns "en"
 *
 * Use as:
 *  - Emergency fallback when both Groq and OpenAI are unavailable
 *  - Works best for English and Hindi; other Indian languages less reliable
 */
@Component
@Slf4j
public class HuggingFaceSttProvider implements SttProvider {

    private static final String BASE_URL = "https://api-inference.huggingface.co";
    private static final String MODEL    = "openai/whisper-large-v3";

    private final WebClient client;
    private final boolean   enabled;

    public HuggingFaceSttProvider(
            WebClient.Builder webClientBuilder,
            @Value("${app.stt.huggingface.api-key:${HUGGINGFACE_API_KEY:}}") String apiKey) {

        this.enabled = apiKey != null && !apiKey.isBlank();
        this.client  = webClientBuilder
            .baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey != null ? apiKey : ""))
            .build();
    }

    @Override public String name()       { return "huggingface-whisper-large-v3"; }
    @Override public boolean isEnabled() { return enabled; }

    @Override
    @SuppressWarnings("unchecked")
    public SpeechToTextService.TranscriptionResult transcribe(byte[] audioBytes,
                                                               String filename) throws Exception {
        // HuggingFace accepts raw audio bytes as the request body
        Object rawResponse = client
            .post()
            .uri("/models/" + MODEL)
            .contentType(MediaType.parseMediaType("audio/ogg"))
            .bodyValue(audioBytes)
            .retrieve()
            .onStatus(s -> s.value() == 503,
                resp -> resp.bodyToMono(String.class).flatMap(b -> {
                    if (b.contains("loading")) {
                        log.warn("{}: model is loading (cold start) — will be ready on next call", name());
                    }
                    return reactor.core.publisher.Mono.error(
                        new RuntimeException(name() + " unavailable: " + b));
                }))
            .onStatus(s -> s.value() == 429,
                resp -> resp.bodyToMono(String.class)
                    .flatMap(b -> reactor.core.publisher.Mono.error(
                        new RateLimitException(name() + " rate limited"))))
            .bodyToMono(Object.class)
            .block();

        if (rawResponse == null) return null;

        // HF Whisper returns: { "text": "transcription" }
        // No language detection — always return "en" as default
        if (rawResponse instanceof Map<?, ?> map && map.get("text") != null) {
            String text = map.get("text").toString().trim();
            if (text.isBlank()) return null;
            log.debug("{}: transcribed {} bytes → '{}'", name(), audioBytes.length,
                      text.length() > 60 ? text.substring(0, 60) + "…" : text);
            // HuggingFace Whisper doesn't return language — default to "en"
            // The NLP layer will detect the actual language from the transcribed text
            return new SpeechToTextService.TranscriptionResult(text, "en");
        }

        log.warn("{}: unexpected response format: {}", name(), rawResponse);
        return null;
    }
}

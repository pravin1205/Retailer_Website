package com.marketly.whatsapp.stt;

import com.marketly.whatsapp.nlp.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * STT provider backed by Groq's hosted Whisper model.
 *
 * Priority: 1 (primary provider)
 *
 * Characteristics:
 *  - Runs openai/whisper-large-v3-turbo on Groq LPU hardware
 *  - IDENTICAL accuracy to OpenAI's Whisper — same model weights
 *  - Cost: ~₹0.05/minute (10x cheaper than OpenAI Whisper)
 *  - Typical latency: 50–200ms for a 10-second voice note (216x realtime speed)
 *  - verbose_json response includes language detection
 *
 * API: https://api.groq.com/openai/v1/audio/transcriptions
 * Format: IDENTICAL multipart/form-data to OpenAI — no code changes needed.
 *
 * Why Groq first:
 *  Same model, same accuracy, 10x cheaper, 10x faster.
 *  OpenAI Whisper is kept as a fallback for Groq outages/rate limits.
 */
@Component
@Slf4j
public class GroqSttProvider implements SttProvider {

    private static final String BASE_URL = "https://api.groq.com";
    private static final String ENDPOINT = "/openai/v1/audio/transcriptions";
    private static final String MODEL    = "whisper-large-v3-turbo";

    private final WebClient client;
    private final boolean   enabled;

    public GroqSttProvider(
            WebClient.Builder webClientBuilder,
            @Value("${app.stt.groq.api-key:${GROQ_API_KEY:}}") String apiKey) {

        this.enabled = apiKey != null && !apiKey.isBlank();
        this.client  = webClientBuilder
            .baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey != null ? apiKey : ""))
            .build();
    }

    @Override public String name()       { return "groq-" + MODEL; }
    @Override public boolean isEnabled() { return enabled; }

    @Override
    @SuppressWarnings("unchecked")
    public SpeechToTextService.TranscriptionResult transcribe(byte[] audioBytes,
                                                               String filename) throws Exception {
        // Groq uses the exact same multipart format as OpenAI Whisper
        MultiValueMap<String, Object> parts = buildMultipartBody(audioBytes, filename);

        Map<String, Object> response = (Map<String, Object>) client
            .post().uri(ENDPOINT)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(parts)
            .retrieve()
            .onStatus(s -> s.value() == 429,
                resp -> resp.bodyToMono(String.class)
                    .map(b -> new RateLimitException(name() + " rate limited")))
            .bodyToMono(Map.class)
            .block();

        // Groq Whisper response format is identical to OpenAI — same parser works
        return SttResultParser.parse(response);
    }

    private MultiValueMap<String, Object> buildMultipartBody(byte[] audioBytes, String filename) {
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(audioBytes) {
            @Override public String getFilename() { return filename; }
        });
        parts.add("model",           MODEL);
        parts.add("response_format", "verbose_json");
        return parts;
    }
}

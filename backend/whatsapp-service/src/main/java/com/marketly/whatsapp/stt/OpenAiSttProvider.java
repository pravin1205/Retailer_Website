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
 * STT provider backed by OpenAI Whisper API.
 *
 * Priority: 2 (fallback to Groq)
 *
 * Characteristics:
 *  - Industry benchmark accuracy for Indian languages
 *  - verbose_json response includes language detection
 *  - Cost: ~₹0.50/minute of audio
 *  - Typical latency: 1–3 seconds per voice note
 *  - Model: whisper-1 (OpenAI's hosted Whisper large-v2)
 *
 * API: https://api.openai.com/v1/audio/transcriptions
 * Format: multipart/form-data with file + model + response_format fields.
 */
@Component
@Slf4j
public class OpenAiSttProvider implements SttProvider {

    private static final String BASE_URL  = "https://api.openai.com";
    private static final String ENDPOINT  = "/v1/audio/transcriptions";
    private static final String MODEL     = "whisper-1";

    private final WebClient client;
    private final boolean   enabled;

    public OpenAiSttProvider(
            WebClient.Builder webClientBuilder,
            @Value("${app.stt.openai.api-key:${app.openai.api-key:}}") String apiKey) {

        this.enabled = apiKey != null && !apiKey.isBlank();
        this.client  = webClientBuilder
            .baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey != null ? apiKey : ""))
            .build();
    }

    @Override public String name()      { return "openai-" + MODEL; }
    @Override public boolean isEnabled(){ return enabled; }

    @Override
    @SuppressWarnings("unchecked")
    public SpeechToTextService.TranscriptionResult transcribe(byte[] audioBytes,
                                                               String filename) throws Exception {
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

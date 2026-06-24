package com.marketly.whatsapp.nlp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * NLP provider backed by HuggingFace Inference API.
 *
 * Priority: 3 (last-resort fallback)
 *
 * Characteristics:
 *  - Free tier available (up to ~30,000 tokens/month on serverless inference)
 *  - Paid tier available for higher throughput
 *  - Model: mistralai/Mistral-7B-Instruct-v0.3 (strong instruction following)
 *  - Cold start latency on free tier: up to 30 seconds (model loading)
 *  - Warm latency: 2–5 seconds
 *  - Quality: good for English, acceptable for Hindi, variable for other Indian languages
 *
 * API differences from OpenAI / Groq:
 *  - Endpoint: https://api-inference.huggingface.co/models/{model}
 *  - Request body: { "inputs": "<prompt>" } instead of structured messages
 *  - Response body: [{ "generated_text": "..." }] (array, not choices)
 *  - The prompt is formatted using the Mistral instruction format [INST]...[/INST]
 *    via {@link NlpPrompts#huggingFacePrompt}
 *
 * Fallback behaviour:
 *  - If the model is loading (cold start), HuggingFace returns HTTP 503 with
 *    { "error": "Model ... is currently loading" }. The chain treats this as a
 *    transient failure and logs it, allowing the service to recover on the next call.
 */
@Component
@Slf4j
public class HuggingFaceNlpProvider implements NlpProvider {

    private static final String BASE_URL = "https://api-inference.huggingface.co";

    private final WebClient    client;
    private final ObjectMapper objectMapper;
    private final String       model;
    private final boolean      enabled;

    public HuggingFaceNlpProvider(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.nlp.huggingface.api-key:${HUGGINGFACE_API_KEY:}}") String apiKey,
            @Value("${app.nlp.huggingface.model:mistralai/Mistral-7B-Instruct-v0.3}") String model) {

        this.objectMapper = objectMapper;
        this.model        = model;
        this.enabled      = apiKey != null && !apiKey.isBlank();

        this.client = webClientBuilder
            .baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey != null ? apiKey : ""))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public String name() { return "huggingface-" + model.replace("/", "-"); }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    @SuppressWarnings("unchecked")
    public NlpService.ExtractionResult extract(String customerMessage) throws Exception {
        // HuggingFace expects a flat "inputs" string — we use the Mistral instruction format
        Map<String, Object> body = Map.of(
            "inputs", NlpPrompts.huggingFacePrompt(customerMessage),
            "parameters", Map.of(
                "max_new_tokens",  512,
                "temperature",     0.1,
                "return_full_text", false  // return only the generated part, not the prompt
            )
        );

        Object rawResponse = client
            .post()
            .uri("/models/" + model)
            .bodyValue(body)
            .retrieve()
            .onStatus(status -> status.value() == 503,
                resp -> resp.bodyToMono(String.class).flatMap(b -> {
                    if (b.contains("loading")) {
                        log.warn("HuggingFace model {} is loading (cold start) — will retry next call", model);
                    }
                    return reactor.core.publisher.Mono.error(
                        new RuntimeException("HuggingFace model unavailable: " + b));
                }))
            .onStatus(status -> status.value() == 429,
                resp -> resp.bodyToMono(String.class)
                    .flatMap(b -> reactor.core.publisher.Mono.error(
                        new RateLimitException(name() + " rate limited: " + b))))
            .bodyToMono(Object.class)
            .block();

        return parseHuggingFaceResponse(rawResponse);
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    /**
     * HuggingFace returns: [{ "generated_text": "{ JSON here }" }]
     * Extract the generated text then hand off to the shared JSON parser.
     */
    @SuppressWarnings("unchecked")
    private NlpService.ExtractionResult parseHuggingFaceResponse(Object rawResponse) {
        if (rawResponse == null) return null;

        String generatedText = null;

        if (rawResponse instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> map) {
                Object gt = map.get("generated_text");
                generatedText = gt != null ? gt.toString().trim() : null;
            }
        }

        if (generatedText == null || generatedText.isBlank()) {
            log.warn("HuggingFace returned empty generated_text");
            return null;
        }

        // The model may include conversational text before the JSON — extract it
        generatedText = extractJsonFromText(generatedText);
        if (generatedText == null) return null;

        try {
            Map<String, Object> fakeOpenAiResponse = Map.of(
                "choices", List.of(
                    Map.of("message", Map.of("content", generatedText))
                )
            );
            return NlpResultParser.parse(fakeOpenAiResponse, objectMapper);
        } catch (Exception e) {
            log.error("HuggingFace result parse error: {} — raw: {}", e.getMessage(), generatedText);
            return null;
        }
    }

    /**
     * HuggingFace models sometimes wrap JSON in conversational text.
     * This extracts the first JSON object from the generated string.
     */
    private String extractJsonFromText(String text) {
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            log.warn("HuggingFace: no JSON object found in: {}", text);
            return null;
        }
        return text.substring(start, end + 1);
    }
}

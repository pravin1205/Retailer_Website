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
 * NLP provider backed by Groq's hosted Llama-3.1-8b-instant model.
 *
 * Priority: 2 (first fallback after OpenAI)
 *
 * Characteristics:
 *  - Uses the exact same OpenAI Chat Completions API format — zero prompt changes
 *  - Groq runs on custom LPU hardware: ~275 tokens/second (much faster than OpenAI)
 *  - Cost: ~10x cheaper than GPT-4o-mini (~₹0.008 per extraction)
 *  - Llama-3.1-8b handles Hindi, Tamil, Telugu well (Meta trained on Indian data)
 *  - Typical latency: 150ms – 400ms (significantly faster than OpenAI)
 *
 * API: Groq Chat Completions — https://api.groq.com/openai/v1/chat/completions
 * The base URL is different but the request/response format is identical to OpenAI.
 *
 * Models available on Groq (in order of speed vs quality):
 *  - llama-3.1-8b-instant    — fastest, good for structured extraction
 *  - llama-3.3-70b-versatile — slower but better for edge-case language handling
 *  - gemma2-9b-it            — Google's Gemma, alternative option
 */
@Component
@Slf4j
public class GroqNlpProvider implements NlpProvider {

    private static final String BASE_URL = "https://api.groq.com";
    private static final String ENDPOINT = "/openai/v1/chat/completions";

    private final WebClient    client;
    private final ObjectMapper objectMapper;
    private final String       model;
    private final int          maxTokens;
    private final double       temperature;
    private final boolean      enabled;

    public GroqNlpProvider(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.nlp.groq.api-key:${GROQ_API_KEY:}}") String apiKey,
            @Value("${app.nlp.groq.model:llama-3.1-8b-instant}") String model,
            @Value("${app.nlp.groq.max-tokens:512}") int maxTokens,
            @Value("${app.nlp.groq.temperature:0.1}") double temperature) {

        this.objectMapper = objectMapper;
        this.model        = model;
        this.maxTokens    = maxTokens;
        this.temperature  = temperature;
        this.enabled      = apiKey != null && !apiKey.isBlank();

        this.client = webClientBuilder
            .baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey != null ? apiKey : ""))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public String name() { return "groq-" + model; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    @SuppressWarnings("unchecked")
    public NlpService.ExtractionResult extract(String customerMessage) throws Exception {
        // Groq uses the EXACT same request format as OpenAI — no changes needed
        Map<String, Object> body = Map.of(
            "model",       model,
            "max_tokens",  maxTokens,
            "temperature", temperature,
            "messages", List.of(
                Map.of("role", "system", "content", NlpPrompts.ORDER_EXTRACTION_SYSTEM),
                Map.of("role", "user",   "content", customerMessage)
            )
        );

        Map<String, Object> response = (Map<String, Object>) client
            .post().uri(ENDPOINT).bodyValue(body)
            .retrieve()
            .onStatus(status -> status.value() == 429,
                resp -> resp.bodyToMono(String.class)
                    .map(b -> new RateLimitException(name() + " rate limited: " + b)))
            .bodyToMono(Map.class)
            .block();

        // Groq response format is identical to OpenAI — same parser works
        return NlpResultParser.parse(response, objectMapper);
    }
}

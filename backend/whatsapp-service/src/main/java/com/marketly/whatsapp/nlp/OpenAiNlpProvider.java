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
 * NLP provider backed by OpenAI GPT-4o-mini.
 *
 * Priority: 1 (primary provider)
 *
 * Characteristics:
 *  - Best overall accuracy for Indian language understanding
 *  - Strong JSON-mode reliability
 *  - Cost: ~₹0.08 per extraction call
 *  - Typical latency: 800ms – 2s
 *
 * API: OpenAI Chat Completions — https://api.openai.com/v1/chat/completions
 */
@Component
@Slf4j
public class OpenAiNlpProvider implements NlpProvider {

    private static final String BASE_URL = "https://api.openai.com";
    private static final String ENDPOINT = "/v1/chat/completions";

    private final WebClient    client;
    private final ObjectMapper objectMapper;
    private final String       model;
    private final int          maxTokens;
    private final double       temperature;
    private final boolean      enabled;

    public OpenAiNlpProvider(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.nlp.openai.api-key:${app.openai.api-key:}}") String apiKey,
            @Value("${app.nlp.openai.model:gpt-4o-mini}") String model,
            @Value("${app.nlp.openai.max-tokens:512}") int maxTokens,
            @Value("${app.nlp.openai.temperature:0.1}") double temperature) {

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
    public String name() { return "openai-" + model; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    @SuppressWarnings("unchecked")
    public NlpService.ExtractionResult extract(String customerMessage) throws Exception {
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

        return NlpResultParser.parse(response, objectMapper);
    }
}

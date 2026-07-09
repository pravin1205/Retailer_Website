package com.marketly.whatsapp.nlp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Shared JSON response parser for NLP extraction results.
 *
 * Both OpenAI and Groq return the same response format:
 *   { "choices": [{ "message": { "content": "{...JSON...}" } }] }
 *
 * HuggingFaceNlpProvider adapts its response to match this format before
 * calling this parser, so a single parsing path handles all three providers.
 */
@Slf4j
final class NlpResultParser {

    private NlpResultParser() {}

    /**
     * Parses a Chat Completions response into an ExtractionResult.
     *
     * @param response    The raw response map from the AI provider.
     * @param objectMapper Jackson mapper for JSON parsing.
     * @return Parsed ExtractionResult, or null if parsing fails.
     */
    @SuppressWarnings("unchecked")
    static NlpService.ExtractionResult parse(Map<String, Object> response,
                                              ObjectMapper objectMapper) {
        if (response == null) return null;

        List<Map<String, Object>> choices =
            (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return null;

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) return null;

        String content = (String) message.get("content");
        if (content == null || content.isBlank()) return null;

        return parseJson(content.trim(), objectMapper);
    }

    @SuppressWarnings("unchecked")
    private static NlpService.ExtractionResult parseJson(String json,
                                                          ObjectMapper objectMapper) {
        try {
            // Strip accidental markdown fences
            String clean = json;
            if (clean.startsWith("```")) {
                clean = clean.replaceAll("```[a-zA-Z]*\\n?", "").replace("```", "").trim();
            }

            Map<String, Object> parsed = objectMapper.readValue(clean, Map.class);

            List<Map<String, Object>> rawItems =
                (List<Map<String, Object>>) parsed.getOrDefault("items", List.of());

            List<NlpService.ExtractedItem> items = rawItems.stream()
                .map(raw -> new NlpService.ExtractedItem(
                    (String) raw.getOrDefault("name", ""),
                    raw.get("quantity") instanceof Number n ? n.doubleValue() : 1.0,
                    (String) raw.get("unit")
                ))
                .filter(i -> i.name() != null && !i.name().isBlank())
                .toList();

            return new NlpService.ExtractionResult(
                items,
                (String) parsed.getOrDefault("language", "en"),
                Boolean.TRUE.equals(parsed.get("reorder_intent")),
                (String) parsed.get("clarification")
            );

        } catch (Exception e) {
            log.error("NlpResultParser: failed to parse JSON — {} — raw: {}", e.getMessage(), json);
            return null;
        }
    }
}

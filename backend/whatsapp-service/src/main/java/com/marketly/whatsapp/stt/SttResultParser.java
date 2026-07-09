package com.marketly.whatsapp.stt;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Shared parser for Whisper API responses.
 *
 * Both OpenAI Whisper and Groq Whisper return the same verbose_json format:
 * <pre>
 * {
 *   "text":     "transcribed text",
 *   "language": "hindi",          // full name OR ISO-639-1 code
 *   "duration": 8.52,
 *   "segments": [...]
 * }
 * </pre>
 *
 * Language codes returned by Whisper are full English names (e.g. "hindi",
 * "tamil") — these are normalised to ISO-639-1 (e.g. "hi", "ta") for
 * consistency with the conversation session language field.
 */
@Slf4j
final class SttResultParser {

    private SttResultParser() {}

    /**
     * Parses a Whisper verbose_json response.
     *
     * @param response Map parsed from the JSON response body.
     * @return TranscriptionResult, or null if parsing fails or text is empty.
     */
    static SpeechToTextService.TranscriptionResult parse(Map<String, Object> response) {
        if (response == null) return null;

        Object textObj = response.get("text");
        if (textObj == null) return null;

        String text = textObj.toString().trim();
        if (text.isBlank()) return null;

        // Whisper may return "language" as full name or ISO code
        String rawLang = response.get("language") != null
            ? response.get("language").toString() : "en";

        String language = normalise(rawLang);

        log.debug("SttResultParser: text='{}' language={}",
                  text.length() > 60 ? text.substring(0, 60) + "…" : text, language);

        return new SpeechToTextService.TranscriptionResult(text, language);
    }

    /** Normalises Whisper language names/codes to ISO-639-1 two-letter codes. */
    private static String normalise(String raw) {
        return switch (raw.toLowerCase()) {
            case "hindi",    "hi" -> "hi";
            case "tamil",    "ta" -> "ta";
            case "telugu",   "te" -> "te";
            case "kannada",  "kn" -> "kn";
            case "bengali",  "bn" -> "bn";
            case "marathi",  "mr" -> "mr";
            case "gujarati", "gu" -> "gu";
            case "punjabi",  "pa" -> "pa";
            default               -> "en";
        };
    }
}

package com.marketly.whatsapp.stt;

import com.marketly.whatsapp.nlp.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Facade for Speech-to-Text transcription with automatic provider failover.
 *
 * Callers (OrderCollectionHandler) interact only with this class and are
 * completely unaware of which STT provider is actually used.
 *
 * Provider chain (ordered by priority in application.yml):
 *   1. Groq Whisper     — whisper-large-v3-turbo (primary: fastest + cheapest)
 *   2. OpenAI Whisper   — whisper-1              (fallback: proven reliability)
 *   3. HuggingFace      — whisper-large-v3       (last resort: free tier)
 *
 * Why Groq as primary for STT:
 *   Groq runs the exact same Whisper model weights as OpenAI but on custom LPU
 *   hardware. The accuracy is identical, the cost is 10x lower, and the latency
 *   is 10x faster. There is no quality tradeoff — it is a pure cost/speed win.
 *
 * Failure handling:
 *  - RateLimitException → logged as WARN, try next provider
 *  - Any other exception → logged as ERROR, try next provider
 *  - All providers exhausted → return null (caller sends "type instead" message)
 */
@Service
@Slf4j
public class SpeechToTextService {

    private final List<SttProvider> providers;

    public SpeechToTextService(List<SttProvider> providers) {
        this.providers = providers.stream()
            .filter(SttProvider::isEnabled)
            .sorted(Comparator.comparingInt(SpeechToTextService::inferPriority))
            .toList();

        if (this.providers.isEmpty()) {
            log.warn("SpeechToTextService: no providers are enabled — voice ordering unavailable. " +
                     "Set at least one of GROQ_API_KEY or OPENAI_API_KEY.");
        } else {
            log.info("SpeechToTextService: active providers in chain order: {}",
                     this.providers.stream().map(SttProvider::name).toList());
        }
    }

    /**
     * Transcribes audio bytes to text.
     * Tries each enabled provider in priority order.
     *
     * @param audioBytes Raw audio (OGG/Opus from WhatsApp voice notes).
     * @param filename   Filename with extension (e.g. "voice.ogg").
     * @return TranscriptionResult, or null if all providers failed.
     */
    public TranscriptionResult transcribe(byte[] audioBytes, String filename) {
        if (audioBytes == null || audioBytes.length == 0) return null;

        for (SttProvider provider : providers) {
            try {
                log.debug("SpeechToTextService: trying provider={}", provider.name());
                TranscriptionResult result = provider.transcribe(audioBytes, filename);

                if (result != null && !result.text().isBlank()) {
                    log.info("SpeechToTextService: succeeded with provider={} lang={}",
                             provider.name(), result.language());
                    return result;
                }

                log.warn("SpeechToTextService: provider={} returned null/empty — trying next",
                         provider.name());

            } catch (RateLimitException e) {
                log.warn("SpeechToTextService: provider={} rate limited — {}",
                         provider.name(), e.getMessage());

            } catch (Exception e) {
                log.error("SpeechToTextService: provider={} failed — {} — trying next",
                          provider.name(), e.getMessage());
            }
        }

        log.error("SpeechToTextService: all providers exhausted — cannot transcribe voice note");
        return null;
    }

    // ── Priority inference ─────────────────────────────────────────────────────

    /** Infers provider priority: Groq = 1, OpenAI = 2, HuggingFace = 3. */
    private static int inferPriority(SttProvider p) {
        String name = p.name().toLowerCase();
        if (name.startsWith("groq"))         return 1;
        if (name.startsWith("openai"))       return 2;
        if (name.startsWith("huggingface"))  return 3;
        return 99;
    }

    // ── Result type (unchanged — callers depend on this) ───────────────────────

    /**
     * Immutable result from a successful STT transcription.
     *
     * @param text     The transcribed text (may be in any Indian language).
     * @param language ISO-639-1 code detected by Whisper
     *                 ("en", "hi", "ta", "te", "kn", "bn", "mr").
     *                 HuggingFace always returns "en" (no language detection).
     */
    public record TranscriptionResult(String text, String language) {}
}

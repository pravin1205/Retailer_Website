package com.marketly.whatsapp.stt;

/**
 * Strategy interface for Speech-to-Text providers.
 *
 * Each implementation wraps a specific STT service
 * (Groq Whisper, OpenAI Whisper, HuggingFace Whisper).
 *
 * The {@link SpeechToTextService} iterates through enabled providers
 * in priority order and returns the first successful transcription.
 *
 * Implementations must:
 *  - Return null (never throw) when the provider is unavailable/unconfigured.
 *  - Throw exceptions only on transient failures so the chain can try next.
 *  - Be stateless and thread-safe.
 */
public interface SttProvider {

    /**
     * Transcribes audio bytes to text.
     *
     * @param audioBytes Raw audio (OGG/Opus from WhatsApp voice notes).
     * @param filename   Filename hint with extension (e.g. "voice.ogg").
     * @return TranscriptionResult, or null if this provider is unavailable.
     * @throws Exception on transient failures — chain will try next provider.
     */
    SpeechToTextService.TranscriptionResult transcribe(byte[] audioBytes,
                                                        String filename) throws Exception;

    /**
     * Human-readable name for logs and metrics.
     * e.g. "groq-whisper-large-v3-turbo", "openai-whisper-1", "huggingface-whisper-large-v3"
     */
    String name();

    /**
     * Returns false when the provider is not configured (missing API key).
     * The chain skips disabled providers silently.
     */
    boolean isEnabled();
}

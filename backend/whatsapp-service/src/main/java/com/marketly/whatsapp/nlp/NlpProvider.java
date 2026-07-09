package com.marketly.whatsapp.nlp;

/**
 * Strategy interface for NLP order-intent extraction providers.
 *
 * Each implementation wraps a specific AI provider (OpenAI, Groq, HuggingFace).
 * The {@link NlpProviderChain} iterates through enabled providers in priority
 * order and returns the first successful extraction.
 *
 * Implementations must:
 *  - Return null (never throw) when the provider is unavailable or misconfigured.
 *  - Throw checked/unchecked exceptions only for transient failures (rate limits,
 *    timeouts, 5xx errors) so the chain can catch them and try the next provider.
 *  - Be stateless and thread-safe.
 */
public interface NlpProvider {

    /**
     * Extracts structured order intent from the customer message.
     *
     * @param customerMessage Raw text (any Indian language or English).
     * @return Structured extraction, or null if this provider is unavailable.
     * @throws Exception on transient failures — chain will try next provider.
     */
    NlpService.ExtractionResult extract(String customerMessage) throws Exception;

    /**
     * Human-readable name used in logs and metrics.
     * e.g. "openai-gpt4o-mini", "groq-llama3", "huggingface-mistral7b"
     */
    String name();

    /**
     * Returns false when the provider is not configured (missing API key).
     * The chain skips disabled providers without logging a warning.
     */
    boolean isEnabled();
}

package com.marketly.whatsapp.nlp;

/**
 * Thrown by NLP and STT providers when the upstream API returns HTTP 429.
 * The provider chain catches this to log a rate-limit warning (distinct from
 * a general failure) and immediately tries the next provider.
 */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}

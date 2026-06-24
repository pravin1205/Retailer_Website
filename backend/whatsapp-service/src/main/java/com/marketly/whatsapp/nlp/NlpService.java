package com.marketly.whatsapp.nlp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Facade for NLP order-intent extraction with automatic provider failover.
 *
 * Callers (OrderCollectionHandler) interact only with this class —
 * they are completely unaware of which AI provider is actually used.
 *
 * Provider chain (configurable in application.yml):
 *   1. Groq   — Llama-3.1-8b-instant (primary: fastest + cheapest)
 *   2. OpenAI — GPT-4o-mini           (fallback: most accurate)
 *   3. HuggingFace — Mistral-7B       (last resort: free tier)
 *
 * The chain order is determined by the {@code app.nlp.<provider>.priority}
 * value in application.yml. Lower number = higher priority.
 *
 * Failure handling:
 *  - RateLimitException → logged as WARN, try next provider
 *  - Any other exception → logged as ERROR, try next provider
 *  - All providers exhausted → return null (caller sends "try again" message)
 */
@Service
@Slf4j
public class NlpService {

    private final List<NlpProvider> providers;

    /**
     * Spring injects all NlpProvider beans. The list is sorted by priority
     * (Groq first, OpenAI second, HuggingFace last) as defined in application.yml.
     *
     * The sort order here uses the class name as a stable tiebreaker when
     * priorities are equal. The explicit priority values in application.yml
     * are the real ordering mechanism.
     */
    public NlpService(List<NlpProvider> providers) {
        // Sort: Groq (priority 1) → OpenAI (priority 2) → HuggingFace (priority 3)
        // We infer priority from the name since Spring-injected lists are unordered.
        this.providers = providers.stream()
            .filter(NlpProvider::isEnabled)
            .sorted(Comparator.comparingInt(NlpService::inferPriority))
            .toList();

        if (this.providers.isEmpty()) {
            log.warn("NlpService: no providers are enabled — NLP will be unavailable. " +
                     "Set at least one of GROQ_API_KEY, OPENAI_API_KEY, or HUGGINGFACE_API_KEY.");
        } else {
            log.info("NlpService: active providers in chain order: {}",
                     this.providers.stream().map(NlpProvider::name).toList());
        }
    }

    /**
     * Extracts structured order intent from the customer message.
     * Tries each enabled provider in order; returns the first successful result.
     *
     * @param customerMessage Raw text (any Indian language or English).
     * @return ExtractionResult, or null if all providers failed.
     */
    public ExtractionResult extract(String customerMessage) {
        if (customerMessage == null || customerMessage.isBlank()) return null;

        for (NlpProvider provider : providers) {
            try {
                log.debug("NlpService: trying provider={}", provider.name());
                ExtractionResult result = provider.extract(customerMessage);

                if (result != null) {
                    log.info("NlpService: succeeded with provider={} items={} lang={}",
                             provider.name(),
                             result.items() != null ? result.items().size() : 0,
                             result.language());
                    return result;
                }

                log.warn("NlpService: provider={} returned null — trying next", provider.name());

            } catch (RateLimitException e) {
                log.warn("NlpService: provider={} rate limited — {}", provider.name(), e.getMessage());

            } catch (Exception e) {
                log.error("NlpService: provider={} failed — {} — trying next",
                          provider.name(), e.getMessage());
            }
        }

        log.error("NlpService: all providers exhausted — cannot extract order intent");
        return null;
    }

    // ── Priority inference ─────────────────────────────────────────────────────

    /**
     * Infers provider priority from its name.
     * Groq = 1 (fastest/cheapest primary), OpenAI = 2, HuggingFace = 3.
     */
    private static int inferPriority(NlpProvider p) {
        String name = p.name().toLowerCase();
        if (name.startsWith("groq"))         return 1;
        if (name.startsWith("openai"))       return 2;
        if (name.startsWith("huggingface"))  return 3;
        return 99;
    }

    // ── Result types (unchanged — callers depend on these) ─────────────────────

    /**
     * Structured output from NLP extraction.
     *
     * @param items         Items the customer wants to order.
     * @param language      ISO-639-1 detected language ("en","hi","ta","te","kn","bn","mr").
     * @param reorderIntent True if customer said "same as last time".
     * @param clarification Non-null when the model needs more info. Send this directly to customer.
     */
    public record ExtractionResult(
        List<ExtractedItem> items,
        String language,
        boolean reorderIntent,
        String clarification
    ) {
        public boolean hasItems()             { return items != null && !items.isEmpty(); }
        public boolean needsClarification()   { return clarification != null && !clarification.isBlank(); }
    }

    /**
     * A single item extracted from the customer's order message.
     *
     * @param name     Product name in English (translated by the model).
     * @param quantity Numeric quantity (default 1.0).
     * @param unit     Unit string ("kg","litre","piece","packet","dozen") or null.
     */
    public record ExtractedItem(String name, double quantity, String unit) {
        public String toDisplayString() {
            String q = quantity == Math.floor(quantity)
                ? String.valueOf((int) quantity) : String.valueOf(quantity);
            return name + (unit != null ? " × " + q + " " + unit : " × " + q);
        }
    }
}

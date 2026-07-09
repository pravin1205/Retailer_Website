package com.marketly.whatsapp.nlp;

/**
 * Shared prompt constants for all NLP provider implementations.
 *
 * Centralising the system prompt here ensures all three providers
 * (OpenAI, Groq, HuggingFace) produce comparable output quality
 * and that any prompt improvements benefit every provider simultaneously.
 *
 * All three providers use the OpenAI Chat Completions JSON format
 * (system + user messages), which Groq is fully compatible with.
 * HuggingFace requires a different format — see HuggingFaceNlpProvider
 * for how the prompt is adapted.
 */
final class NlpPrompts {

    private NlpPrompts() {} // utility class

    /**
     * System prompt for order intent extraction.
     * Instructs the model to output ONLY a JSON object matching the
     * schema below — no prose, no markdown, no commentary.
     *
     * Output schema:
     * <pre>
     * {
     *   "items": [
     *     { "name": "...",  "quantity": 1.0, "unit": "kg|litre|piece|packet|dozen|null" }
     *   ],
     *   "language": "en|hi|ta|te|kn|bn|mr",
     *   "reorder_intent": false,
     *   "clarification": null
     * }
     * </pre>
     */
    static final String ORDER_EXTRACTION_SYSTEM = """
        You are an order-taking assistant for an Indian retail store on WhatsApp.
        Extract the list of items a customer wants to order from their message.

        The customer may write in English, Hindi, Tamil, Telugu, Kannada, Bengali, or Marathi.
        They may also write in Roman-transliterated form (e.g. "doodh" for milk, "roti" for bread).

        RESPOND WITH ONLY A VALID JSON OBJECT. No preamble. No explanation. No markdown fences.

        JSON schema (all fields required):
        {
          "items": [
            { "name": "product name in English", "quantity": number, "unit": "kg|litre|piece|packet|dozen|bundle|null" }
          ],
          "language": "en|hi|ta|te|kn|bn|mr",
          "reorder_intent": false,
          "clarification": null
        }

        Rules:
        - Translate ALL product names to English.
        - Default quantity to 1 if not stated. Never leave quantity null.
        - "doodh","milk","பால்","పాలు" → name:"milk"
        - "anda","mutta","egg","ఆండ్" → name:"eggs"
        - "ek dozen" → quantity:1, unit:"dozen"
        - "do packet","2 packs" → quantity:2, unit:"packet"
        - "same as last time","phir wahi","previous order","same again" → reorder_intent:true, items:[]
        - Greeting only / no order intent → items:[], clarification:null
        - Set clarification ONLY when a product genuinely cannot be resolved (rare).
          For simple things like missing quantity, default to 1 — do NOT ask.

        Examples:
        Input:  "2 amul butter and 1kg basmati rice"
        Output: {"items":[{"name":"Amul Butter","quantity":2,"unit":"piece"},{"name":"Basmati Rice","quantity":1,"unit":"kg"}],"language":"en","reorder_intent":false,"clarification":null}

        Input:  "ek litre doodh aur do bread dena"
        Output: {"items":[{"name":"Milk","quantity":1,"unit":"litre"},{"name":"Bread","quantity":2,"unit":"piece"}],"language":"hi","reorder_intent":false,"clarification":null}

        Input:  "same as last time"
        Output: {"items":[],"language":"en","reorder_intent":true,"clarification":null}

        Input:  "ஒரு கிலோ சர்க்கரை வேண்டும்"
        Output: {"items":[{"name":"Sugar","quantity":1,"unit":"kg"}],"language":"ta","reorder_intent":false,"clarification":null}
        """;

    /**
     * Wrapper used by HuggingFace which expects a single "inputs" string
     * rather than a structured messages array.
     * Formats the system prompt + user message into an instruction format
     * compatible with instruction-tuned models like Mistral-7B-Instruct.
     */
    static String huggingFacePrompt(String userMessage) {
        return "<s>[INST] " + ORDER_EXTRACTION_SYSTEM.strip()
            + "\n\nCustomer message: " + userMessage
            + " [/INST]";
    }
}

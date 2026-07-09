package com.marketly.whatsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WhatsApp ordering service.
 *
 * Responsibilities:
 *  1. Receives inbound messages from all sellers' connected WhatsApp numbers
 *     via a single Meta webhook endpoint.
 *  2. Resolves each message to the correct seller (tenant) using the
 *     phone_number_id → tenantId mapping stored in Redis / tenant-service.
 *  3. Manages multi-turn order conversations in Redis (COLLECTING_ITEMS,
 *     ADDRESS_SELECT, AWAITING_PAYMENT, etc.).
 *  4. Transcribes voice notes via Groq Whisper / OpenAI Whisper.
 *  5. Extracts order intent from text using Groq Llama / OpenAI GPT-4o-mini.
 *  6. Builds carts and places orders by calling the existing platform APIs
 *     (product-service, order-service, customer-service) — identical to
 *     what the web frontend does.
 *  7. Generates Razorpay payment links and sends them in-chat.
 *  8. Consumes Kafka order lifecycle events and forwards status updates to
 *     customers as WhatsApp messages — from the seller's own number.
 *
 * This service has NO database of its own. All persistence uses Redis
 * (conversation sessions with TTL) or the existing platform databases
 * via service-to-service REST calls.
 *
 * @Async is enabled via {@link com.marketly.whatsapp.config.AsyncConfig}.
 * Service discovery (Eureka) is intentionally disabled — inter-service
 * communication uses direct URLs configured in application.yml.
 */
@SpringBootApplication
public class WhatsappServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatsappServiceApplication.class, args);
    }
}

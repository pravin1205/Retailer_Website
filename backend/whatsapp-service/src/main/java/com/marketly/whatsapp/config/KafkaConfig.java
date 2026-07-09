package com.marketly.whatsapp.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for the whatsapp-service.
 *
 * Defines the {@code kafkaListenerContainerFactory} bean explicitly — required because
 * the auto-configured factory does not include the JsonDeserializer trusted packages
 * setting, causing deserialization failures when receiving order lifecycle events
 * (which are serialised as JSON maps by the order-service Kafka producer).
 *
 * All three topics consumed by {@link com.marketly.whatsapp.consumer.OrderStatusConsumer}
 * use this factory:
 *   - order.order.created
 *   - order.order.status-changed
 *   - order.order.cancelled
 *
 * Event envelope structure produced by order-service:
 *   { eventType, tenantId, producedBy, payload: { orderId, orderNumber, source, ... } }
 * Deserialised into {@code Map<String, Object>} — no custom DTO classes needed.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:whatsapp-service-group}")
    private String defaultGroupId;

    /**
     * Consumer factory that deserialises Kafka messages as Map<String, Object>.
     * Used by the listener container factory below.
     */
    @Bean
    public ConsumerFactory<String, Map<String, Object>> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,            defaultGroupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Trust all packages — the event payload is a plain Map with primitive values.
        // Restricting to specific packages is unnecessary for Map deserialization.
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props,
            new StringDeserializer(),
            new JsonDeserializer<>(Map.class, false));
    }

    /**
     * Listener container factory referenced by all @KafkaListener annotations
     * in this service via {@code containerFactory = "kafkaListenerContainerFactory"}.
     *
     * Concurrency is set to 1 — all three topics have low-volume consumption
     * (only WHATSAPP-sourced order events trigger any work). Increase to 3 when
     * the number of Premium sellers grows significantly.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>>
            kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(1);
        // Error handling: log and continue — don't stop consuming on a bad message.
        // A single malformed event should not block all future order notifications.
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler());

        return factory;
    }
}

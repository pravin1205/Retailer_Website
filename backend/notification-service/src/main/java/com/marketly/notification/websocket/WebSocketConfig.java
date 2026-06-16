package com.marketly.notification.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket configuration.
 *
 * Clients connect to:   ws://host/ws
 * Subscribe to topics:  /topic/orders/{tenantId}/{customerId}
 *                       /topic/store/{tenantId}
 * Send messages to:     /app/...
 *
 * In-memory broker is used for single-instance dev.
 * For multi-instance production, replace with Redis pub/sub broker relay.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();   // fallback for browsers without native WebSocket
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Client subscribes to /topic/... destinations
        config.enableSimpleBroker("/topic");
        // Server-side @MessageMapping methods receive from /app/...
        config.setApplicationDestinationPrefixes("/app");
    }
}

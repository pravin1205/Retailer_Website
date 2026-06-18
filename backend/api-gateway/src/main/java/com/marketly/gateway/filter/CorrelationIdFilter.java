package com.marketly.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Generates or propagates an X-Correlation-ID header on every request.
 *
 * Flow:
 *  1. Check if the incoming request already carries X-Correlation-ID (e.g. from mobile app).
 *  2. If not, generate a new UUID.
 *  3. Inject it into the forwarded request headers so ALL downstream services receive it.
 *  4. Inject it into the response headers so API clients can correlate logs.
 *
 * Downstream services read X-Correlation-ID and put it into MDC for structured logging.
 */
@Component
@Slf4j
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders()
            .getFirst(CORRELATION_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        final String finalCorrelationId = correlationId;

        // Inject correlation ID into the forwarded downstream request
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .header(CORRELATION_HEADER, finalCorrelationId)
            .build();

        // Add correlation ID to the response headers BEFORE the chain runs.
        // Response headers must be set before the first write; doFinally fires
        // after the response is committed which causes ReadOnlyHttpHeaders to throw.
        exchange.getResponse().getHeaders()
            .add(CORRELATION_HEADER, finalCorrelationId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -200; // Before JwtAuthFilter (-100)
    }
}

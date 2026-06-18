package com.marketly.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Global filter that resolves the tenant context from the request hostname
 * for PUBLIC (unauthenticated) requests.
 *
 * <p>When a customer browses {@code freshmart.marketly.com/products} without a
 * JWT, there is no {@code tenant_id} claim to read. This filter extracts the
 * subdomain slug from the {@code Host} header, resolves it to a tenant UUID via
 * tenant-service, and injects {@code X-Tenant-ID} so downstream services can
 * activate their Hibernate tenant filter even on unauthenticated requests.</p>
 *
 * <p>If the request already carries a JWT (handled by {@link JwtAuthFilter} at
 * order -100), the {@code X-Tenant-ID} set here is overwritten by the value from
 * the JWT claim, which is the authoritative source.</p>
 *
 * <p>Order is -200 — runs BEFORE JwtAuthFilter so that the header is always
 * present when downstream receives the request.</p>
 *
 * <p>Results are cached in-process for 30 minutes to avoid repeated calls to
 * tenant-service on every public product browse request.</p>
 */
@Component
@Slf4j
public class TenantResolutionFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -200;
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30);

    private final String platformDomain;
    private final WebClient tenantWebClient;

    /** Simple in-process cache: slug → (tenantId, expiresAt) */
    private final ConcurrentHashMap<String, CacheEntry> slugCache = new ConcurrentHashMap<>();

    public TenantResolutionFilter(
            @Value("${app.platform-domain:marketly.com}") String platformDomain,
            @Value("${app.tenant-service-url:http://localhost:8082}") String tenantServiceUrl) {
        this.platformDomain  = platformDomain;
        this.tenantWebClient = WebClient.builder().baseUrl(tenantServiceUrl).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String host = exchange.getRequest().getHeaders().getFirst("Host");
        if (host == null) {
            return chain.filter(exchange);
        }

        // Strip port if present (e.g. freshmart.marketly.com:8080 → freshmart.marketly.com)
        host = host.split(":")[0].toLowerCase();

        // Only process subdomain requests: freshmart.marketly.com → slug = "freshmart"
        // Exclude: marketly.com, admin.marketly.com, seller.marketly.com, localhost
        String slug = extractSlug(host);
        if (slug == null) {
            return chain.filter(exchange);
        }

        // Check in-process cache first
        CacheEntry cached = slugCache.get(slug);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt) {
            return chain.filter(withTenantHeader(exchange, cached.tenantId));
        }

        // Call tenant-service to resolve slug → UUID
        return tenantWebClient.get()
            .uri("/api/v1/tenants/{slug}", slug)
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> extractTenantId(response))
            .flatMap(tenantId -> {
                if (tenantId != null) {
                    slugCache.put(slug, new CacheEntry(tenantId,
                        System.currentTimeMillis() + CACHE_TTL_MS));
                    log.debug("Resolved slug '{}' → tenantId={}", slug, tenantId);
                    return chain.filter(withTenantHeader(exchange, tenantId));
                }
                return chain.filter(exchange);
            })
            .onErrorResume(ex -> {
                // Tenant-service unavailable — continue without X-Tenant-ID (public listing
                // will return empty results rather than cross-tenant data, which is safe)
                log.warn("TenantResolutionFilter: could not resolve slug '{}': {}", slug, ex.getMessage());
                return chain.filter(exchange);
            });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts the store slug from the Host header.
     * Returns null for non-store subdomains (admin, seller, localhost, raw domain).
     */
    private String extractSlug(String host) {
        if (host == null || !host.endsWith("." + platformDomain)) return null;

        String subdomain = host.substring(0, host.length() - platformDomain.length() - 1);
        // Exclude platform-reserved subdomains
        if (subdomain.isEmpty()
            || subdomain.equals("admin")
            || subdomain.equals("seller")
            || subdomain.equals("api")
            || subdomain.equals("www")) {
            return null;
        }
        return subdomain;
    }

    @SuppressWarnings("unchecked")
    private String extractTenantId(Map<?, ?> response) {
        if (response == null) return null;
        // Handle { "success": true, "data": { "id": "uuid" } } envelope
        Object data = response.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object id = dataMap.get("id");
            return id != null ? id.toString() : null;
        }
        // Also handle flat response (some endpoints return tenant directly)
        Object id = response.get("id");
        return id != null ? id.toString() : null;
    }

    private ServerWebExchange withTenantHeader(ServerWebExchange exchange, String tenantId) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
            .header("X-Tenant-ID", tenantId)
            .build();
        return exchange.mutate().request(mutated).build();
    }

    private record CacheEntry(String tenantId, long expiresAt) {}
}

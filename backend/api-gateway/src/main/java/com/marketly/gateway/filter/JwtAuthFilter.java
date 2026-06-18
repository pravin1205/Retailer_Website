package com.marketly.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Global filter that validates JWT tokens on all protected routes.
 *
 * Public paths are explicitly excluded (see isPublicPath).
 * On valid JWT, injects X-User-ID, X-Tenant-ID, and X-Roles headers
 * so downstream services can trust them without re-validating.
 */
@Component
@Slf4j
public class JwtAuthFilter implements GlobalFilter, Ordered {

    // Exact prefix matches that are ALWAYS public regardless of method
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
        "/api/v1/auth/",      // all auth endpoints
        "/actuator/health",
        "/v3/api-docs",
        "/swagger-ui"
    );

    // GET-only public paths (products and categories are read-public; write operations require auth)
    private static final List<String> PUBLIC_GET_PREFIXES = List.of(
        "/api/v1/products",
        "/api/v1/categories"
    );

    // GET /api/v1/tenants          → public list
    // GET /api/v1/tenants/{slug}   → public single (storefront landing, slug check)
    // All POST/PATCH/PUT/DELETE on /api/v1/tenants/** → require auth
    private static final Pattern PUBLIC_TENANT_GET =
        Pattern.compile("^/api/v1/tenants(/[^/]+)?$");

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (isPublicPath(exchange)) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = parseToken(token);

            String userId   = claims.getSubject();
            String tenantId = claims.get("tenant_id", String.class);

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            String rolesHeader = roles != null ? String.join(",", roles) : "";

            // Remove any X-Tenant-ID already set by TenantResolutionFilter (order -200)
            // then re-set it authoritatively from the JWT claim.
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-ID");
                    h.remove("X-Roles");
                    h.remove("X-Tenant-ID");
                })
                .header("X-User-ID",   userId)
                .header("X-Roles",     rolesHeader)
                .header("X-Tenant-ID", tenantId != null ? tenantId : "")
                .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed for path {}: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private boolean isPublicPath(ServerWebExchange exchange) {
        String path   = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        // Always-public prefixes (auth endpoints, docs, health)
        if (PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith)) return true;

        // GET-only public paths (products, categories)
        if (HttpMethod.GET.equals(method) &&
            PUBLIC_GET_PREFIXES.stream().anyMatch(path::startsWith)) return true;

        // GET /api/v1/tenants  and  GET /api/v1/tenants/{slug}  are public
        // Everything else on /api/v1/tenants/** (POST, PATCH, PUT, sub-paths like /kyc, /approve, /review) requires auth
        if (HttpMethod.GET.equals(method) && PUBLIC_TENANT_GET.matcher(path).matches()) return true;

        return false;
    }

    @Override
    public int getOrder() {
        return -100;  // Run before other filters
    }
}

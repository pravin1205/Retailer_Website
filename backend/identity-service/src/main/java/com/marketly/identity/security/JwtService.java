package com.marketly.identity.security;

import com.marketly.identity.entity.User;
import com.marketly.identity.entity.UserTenantRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final JwtProperties jwtProperties;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(
            jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Generate a short-lived access token.
     * Embeds user_id, email, tenant_id (if present), and roles.
     */
    public String generateAccessToken(User user, UUID tenantId) {
        Instant now    = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getAccessTtlSeconds());

        List<String> roles = user.getTenantRoles().stream()
            .filter(utr -> tenantId == null || tenantId.equals(utr.getTenantId()))
            .filter(utr -> utr.getDeletedAt() == null)
            .map(utr -> utr.getRole().getName())
            .distinct()
            .toList();

        var builder = Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("roles", roles)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey());

        if (tenantId != null) {
            builder.claim("tenant_id", tenantId.toString());
        }

        return builder.compact();
    }

    /**
     * Parse and validate a JWT. Returns the Claims on success.
     * Throws JwtException on invalid/expired token.
     */
    public Claims validateAndExtract(String token) {
        return Jwts.parser()
            .verifyWith(signingKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isValid(String token) {
        try {
            validateAndExtract(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String extractUserId(String token) {
        return validateAndExtract(token).getSubject();
    }
}

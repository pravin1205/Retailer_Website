package com.marketly.identity.service;

import com.marketly.common.exception.DuplicateResourceException;
import com.marketly.common.exception.UnauthorizedException;
import com.marketly.identity.dto.LoginRequest;
import com.marketly.identity.dto.LoginResponse;
import com.marketly.identity.dto.RegisterRequest;
import com.marketly.identity.entity.RefreshToken;
import com.marketly.identity.entity.User;
import com.marketly.identity.event.UserEventProducer;
import com.marketly.identity.repository.RefreshTokenRepository;
import com.marketly.identity.repository.UserRepository;
import com.marketly.identity.security.JwtProperties;
import com.marketly.identity.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    private final UserRepository         userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder        passwordEncoder;
    private final JwtService             jwtService;
    private final JwtProperties          jwtProperties;
    private final UserEventProducer      userEventProducer;
    private final RestClient             tenantRestClient;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            UserEventProducer userEventProducer,
            @Value("${app.tenant-service-url:http://localhost:8082}") String tenantServiceUrl) {
        this.userRepository       = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder      = passwordEncoder;
        this.jwtService           = jwtService;
        this.jwtProperties        = jwtProperties;
        this.userEventProducer    = userEventProducer;
        this.tenantRestClient     = RestClient.builder().baseUrl(tenantServiceUrl).build();
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new DuplicateResourceException(
                "An account with email '" + request.getEmail() + "' already exists.");
        }

        User user = User.builder()
            .email(request.getEmail().toLowerCase().trim())
            .phone(request.getPhone())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .active(true)
            .verified(false)
            .build();

        user = userRepository.save(user);

        // Role assignment happens via OtpService (OTP flow) or TenantEventConsumer (Kafka).
        // Email+password registration does not assign a tenant-scoped role here
        // because the tenantSlug → tenantId resolution and role row insert requires
        // an async call to tenant-service, which is handled separately.
        log.info("User registered: {}", user.getEmail());

        userEventProducer.publishUserRegistered(user, request.getTenantSlug());

        return user;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailWithRoles(request.getEmail().toLowerCase().trim())
            .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is disabled.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password.");
        }

        // Resolve tenant ID from tenantSlug — scopes the JWT to a specific store
        String tenantSlug = request.getTenantSlug();
        UUID tenantId = resolveTenantId(tenantSlug);

        // Generate tokens
        String accessToken  = jwtService.generateAccessToken(user, tenantId);
        String rawRefresh   = UUID.randomUUID().toString();
        String refreshHash  = sha256(rawRefresh);

        RefreshToken refreshToken = RefreshToken.builder()
            .user(user)
            .tenantId(tenantId)
            .tokenHash(refreshHash)
            .expiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTtlDays() * 86400L))
            .build();
        refreshTokenRepository.save(refreshToken);

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        List<String> roles = user.getTenantRoles().stream()
            .filter(utr -> utr.getDeletedAt() == null)
            .filter(utr -> tenantId == null || tenantId.equals(utr.getTenantId()))
            .map(utr -> utr.getRole().getName())
            .distinct()
            .toList();

        return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(rawRefresh)
            .tokenType("Bearer")
            .expiresIn(jwtProperties.getAccessTtlSeconds())
            .user(LoginResponse.UserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .roles(roles)
                .tenantId(tenantId)
                .tenantSlug(tenantSlug)
                .build())
            .build();
    }

    @Transactional
    public LoginResponse refreshToken(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token."));

        if (!storedToken.isValid()) {
            throw new UnauthorizedException("Refresh token has expired or been revoked.");
        }

        // Rotate: revoke old, issue new
        storedToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        String newAccessToken = jwtService.generateAccessToken(user, storedToken.getTenantId());
        String newRawRefresh  = UUID.randomUUID().toString();
        String newHash        = sha256(newRawRefresh);

        RefreshToken newRefresh = RefreshToken.builder()
            .user(user)
            .tenantId(storedToken.getTenantId())
            .tokenHash(newHash)
            .expiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTtlDays() * 86400L))
            .build();
        refreshTokenRepository.save(newRefresh);

        return LoginResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRawRefresh)
            .tokenType("Bearer")
            .expiresIn(jwtProperties.getAccessTtlSeconds())
            .build();
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevokedAt(Instant.now());
            refreshTokenRepository.save(rt);
        });
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found."));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Revoke all refresh tokens on password change
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    /**
     * Calls tenant-service to resolve a slug to a tenant UUID.
     * Returns null if slug is blank, not found, or service is unavailable.
     */
    @SuppressWarnings("unchecked")
    private UUID resolveTenantId(String slug) {
        if (slug == null || slug.isBlank()) return null;
        try {
            Map<String, Object> response = tenantRestClient.get()
                .uri("/api/v1/tenants/{slug}", slug)
                .retrieve()
                .body(Map.class);
            if (response == null) return null;
            Object data = response.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Object id = dataMap.get("id");
                if (id != null) return UUID.fromString(id.toString());
            }
            return null;
        } catch (RestClientException e) {
            log.warn("Could not resolve tenantId for slug='{}': {}", slug, e.getMessage());
            return null;
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

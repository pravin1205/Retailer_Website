package com.marketly.identity.service;

import com.marketly.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import com.marketly.common.exception.UnauthorizedException;
import com.marketly.identity.dto.OtpVerifyRequest;
import com.marketly.identity.dto.OtpVerifyResponse;
import com.marketly.identity.entity.RefreshToken;
import com.marketly.identity.entity.Role;
import com.marketly.identity.entity.User;
import com.marketly.identity.entity.UserTenantRole;
import com.marketly.identity.event.UserEventProducer;
import com.marketly.identity.repository.RefreshTokenRepository;
import com.marketly.identity.repository.RoleRepository;
import com.marketly.identity.repository.UserRepository;
import com.marketly.identity.repository.UserTenantRoleRepository;
import com.marketly.identity.security.JwtProperties;
import com.marketly.identity.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * OTP-based authentication service.
 *
 * OTPs are stored in Redis under key "otp:{phone}" as a SHA-256 hash.
 * TTL is 10 minutes. Rate limiting: max 5 sends per phone per 10 min.
 *
 * On successful verification:
 * - New users are created with a phone-derived placeholder email.
 * - If tenantSlug is provided, a UserTenantRole row is inserted (idempotent).
 * - The JWT is scoped to the tenant when tenantSlug resolves to a UUID.
 */
@Service
@Slf4j
public class OtpService {

    private static final String OTP_KEY_PREFIX       = "otp:";
    private static final String OTP_RATE_KEY_PREFIX  = "otp:rate:";
    private static final Duration OTP_TTL            = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS_PER_WINDOW = 5;

    private final StringRedisTemplate        redisTemplate;
    private final UserRepository             userRepository;
    private final RoleRepository             roleRepository;
    private final UserTenantRoleRepository   userTenantRoleRepository;
    private final RefreshTokenRepository     refreshTokenRepository;
    private final PasswordEncoder            passwordEncoder;
    private final JwtService                 jwtService;
    private final JwtProperties              jwtProperties;
    private final UserEventProducer          userEventProducer;
    private final RestClient                 tenantRestClient;

    public OtpService(
            StringRedisTemplate redisTemplate,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserTenantRoleRepository userTenantRoleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            UserEventProducer userEventProducer,
            @Value("${app.tenant-service-url:http://localhost:8082}") String tenantServiceUrl) {
        this.redisTemplate           = redisTemplate;
        this.userRepository          = userRepository;
        this.roleRepository          = roleRepository;
        this.userTenantRoleRepository = userTenantRoleRepository;
        this.refreshTokenRepository  = refreshTokenRepository;
        this.passwordEncoder         = passwordEncoder;
        this.jwtService              = jwtService;
        this.jwtProperties           = jwtProperties;
        this.userEventProducer       = userEventProducer;
        this.tenantRestClient        = RestClient.builder().baseUrl(tenantServiceUrl).build();
    }

    // ── Send OTP ──────────────────────────────────────────────────────────────

    /**
     * Generates a 6-digit OTP, stores it hashed in Redis, and returns the
     * plain-text OTP for the notification system to dispatch.
     */
    public String generateAndStore(String phone) {
        enforceRateLimit(phone);

        String otpPlain = String.format("%06d", new Random().nextInt(1_000_000));
        String otpHash  = sha256(otpPlain);

        redisTemplate.opsForValue().set(OTP_KEY_PREFIX + phone, otpHash, OTP_TTL);

        String rateKey = OTP_RATE_KEY_PREFIX + phone;
        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateKey, OTP_TTL);
        }

        log.info("OTP generated for phone={}", maskPhone(phone));
        return otpPlain;
    }

    // ── Verify OTP + Issue JWT ────────────────────────────────────────────────

    @Transactional
    public OtpVerifyResponse verifyAndIssueToken(OtpVerifyRequest request) {
        String phone    = request.getPhone();
        String redisKey = OTP_KEY_PREFIX + phone;

        String storedHash = redisTemplate.opsForValue().get(redisKey);
        if (storedHash == null) {
            throw new BusinessException("OTP_EXPIRED",
                "OTP has expired. Please request a new one.", HttpStatus.BAD_REQUEST);
        }
        if (!storedHash.equals(sha256(request.getOtp()))) {
            throw new UnauthorizedException("Invalid OTP. Please check and try again.");
        }

        // One-time use — delete immediately after successful validation
        redisTemplate.delete(redisKey);

        // ── Resolve tenantSlug → tenantId ─────────────────────────────────────
        UUID tenantId = resolveTenantId(request.getTenantSlug());

        // ── Find or create the user ───────────────────────────────────────────
        boolean isNewUser = !userRepository.existsByPhoneAndDeletedAtIsNull(phone);
        User user;
        if (isNewUser) {
            user = createUserFromPhone(phone, request.getTenantSlug());
        } else {
            user = userRepository.findByPhoneWithRoles(phone)
                .orElseThrow(() -> new UnauthorizedException("User not found."));
        }

        // ── Assign role for this tenant (idempotent) ──────────────────────────
        if (tenantId != null) {
            String roleName = isTenantOwnerRequest(request.getRole()) ? "TENANT_OWNER" : "CUSTOMER";
            assignRoleIfAbsent(user, tenantId, roleName);
            // Re-fetch to get freshly loaded tenantRoles for JWT generation
            user = userRepository.findByPhoneWithRoles(phone)
                .orElseThrow(() -> new UnauthorizedException("User not found after role assignment."));
        }

        // ── Issue tokens ──────────────────────────────────────────────────────
        String accessToken = jwtService.generateAccessToken(user, tenantId);
        String rawRefresh  = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
            .user(user)
            .tenantId(tenantId)
            .tokenHash(sha256(rawRefresh))
            .expiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTtlDays() * 86400L))
            .build();
        refreshTokenRepository.save(refreshToken);

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Collect roles for this tenant context (or all if no tenant)
        final UUID finalTenantId = tenantId;
        List<String> roles = user.getTenantRoles().stream()
            .filter(utr -> utr.getDeletedAt() == null)
            .filter(utr -> finalTenantId == null || finalTenantId.equals(utr.getTenantId()))
            .map(utr -> utr.getRole().getName())
            .distinct()
            .toList();

        return OtpVerifyResponse.builder()
            .verified(true)
            .isNewUser(isNewUser)
            .accessToken(accessToken)
            .refreshToken(rawRefresh)
            .tokenType("Bearer")
            .expiresIn(jwtProperties.getAccessTtlSeconds())
            .user(OtpVerifyResponse.UserInfo.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .roles(roles)
                .tenantSlug(request.getTenantSlug())
                .build())
            .build();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private User createUserFromPhone(String phone, String tenantSlug) {
        User user = User.builder()
            .phone(phone)
            .email(phone + "@otp.marketly.internal")  // placeholder; real email set in profile step
            .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
            .active(true)
            .verified(true)
            .build();
        user = userRepository.save(user);

        log.info("OTP user created: phone={}", maskPhone(phone));
        userEventProducer.publishUserRegistered(user, tenantSlug);
        return user;
    }

    /**
     * Inserts a UserTenantRole row for (user, tenant, role) if one does not
     * already exist. Safe to call multiple times — fully idempotent.
     */
    private void assignRoleIfAbsent(User user, UUID tenantId, String roleName) {
        boolean exists = userTenantRoleRepository
            .existsByUserIdAndTenantIdAndRoleNameAndDeletedAtIsNull(
                user.getId(), tenantId, roleName);
        if (exists) {
            log.debug("Role {} already assigned for user={} tenant={}", roleName, user.getId(), tenantId);
            return;
        }

        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new IllegalStateException(roleName + " role not seeded"));

        UserTenantRole utr = UserTenantRole.builder()
            .user(user)
            .tenantId(tenantId)
            .role(role)
            .build();
        userTenantRoleRepository.save(utr);
        log.info("Role {} assigned: userId={} tenantId={}", roleName, user.getId(), tenantId);
    }

    /**
     * Calls tenant-service to resolve a slug to a UUID.
     * Returns null if slug is blank, tenant not found, or service unavailable.
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
            // Response envelope: { "success": true, "data": { "id": "uuid", ... } }
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

    private boolean isTenantOwnerRequest(String role) {
        return "TENANT_OWNER".equalsIgnoreCase(role) || "SELLER".equalsIgnoreCase(role);
    }

    private void enforceRateLimit(String phone) {
        String countStr = redisTemplate.opsForValue().get(OTP_RATE_KEY_PREFIX + phone);
        if (countStr != null && Integer.parseInt(countStr) >= MAX_ATTEMPTS_PER_WINDOW) {
            throw new BusinessException("RATE_LIMITED",
                "Too many OTP requests. Please wait 10 minutes before trying again.",
                HttpStatus.TOO_MANY_REQUESTS);
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

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return "******" + phone.substring(phone.length() - 4);
    }
}

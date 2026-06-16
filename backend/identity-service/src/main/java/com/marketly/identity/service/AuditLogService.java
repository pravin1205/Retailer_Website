package com.marketly.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Writes immutable security audit events to identity.audit_log.
 *
 * Uses JdbcTemplate directly (not JPA) so the insert is always committed
 * independently — even if the outer transaction rolls back (e.g. on
 * failed login, we still want to record the attempt).
 *
 * All writes are @Async to avoid adding latency to the auth request path.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final JdbcTemplate jdbc;

    @Async
    public void log(String eventType, UUID userId, UUID tenantId,
                    String ipAddress, String userAgent) {
        try {
            jdbc.update(
                "INSERT INTO identity.audit_log " +
                "(id, user_id, tenant_id, event_type, ip_address, user_agent, occurred_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, ?::inet, ?, now())",
                userId, tenantId, eventType,
                ipAddress, userAgent
            );
        } catch (Exception e) {
            // Audit failure must never crash the main flow
            log.error("Failed to write audit log [{}] for user {}: {}",
                      eventType, userId, e.getMessage());
        }
    }

    public static final String LOGIN_SUCCESS     = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED      = "LOGIN_FAILED";
    public static final String LOGOUT            = "LOGOUT";
    public static final String PASSWORD_CHANGED  = "PASSWORD_CHANGED";
    public static final String TOKEN_REVOKED     = "TOKEN_REVOKED";
    public static final String REGISTER          = "REGISTER";
}

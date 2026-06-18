package com.marketly.common.tenant;

import java.util.UUID;

/**
 * Platform-wide constants shared across all microservices.
 *
 * PLATFORM_TENANT_ID is a sentinel UUID used in user_tenant_roles for platform-level
 * roles (SUPER_ADMIN) that are not scoped to any specific tenant.
 * When generating a JWT for a SUPER_ADMIN, tenantId is passed as null so the
 * token has no tenant_id claim — giving full cross-tenant access.
 */
public final class PlatformConstants {

    private PlatformConstants() {}

    /**
     * Sentinel UUID representing the platform itself (not any real tenant).
     * Used as tenant_id in user_tenant_roles for SUPER_ADMIN role assignments.
     */
    public static final UUID PLATFORM_TENANT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");
}

package com.marketly.common.tenant;

import java.util.UUID;

/**
 * ThreadLocal holder for the current request's tenant ID.
 *
 * Set once by the API Gateway's TenantResolutionFilter (via X-Tenant-ID header),
 * then readable anywhere within the same request thread.
 *
 * Must always be cleared at the end of each request to prevent leaking
 * tenant context across thread-pool threads.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID get() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}

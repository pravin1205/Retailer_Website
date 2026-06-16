package com.marketly.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter added to every downstream microservice (not the gateway).
 *
 * Reads X-Correlation-ID, X-User-ID, and X-Tenant-ID from the forwarded
 * request and puts them into SLF4J MDC so every log line in that request
 * thread automatically includes them.
 *
 * Example log line:
 *   2026-06-16 10:30:00 INFO  [correlationId=abc-123] [userId=uuid] [tenantId=uuid]
 *   ProductController - GET /api/v1/products returned 20 items
 */
@Component
@Order(1)
public class MdcRequestFilter implements Filter {

    private static final String CORRELATION_ID = "correlationId";
    private static final String USER_ID        = "userId";
    private static final String TENANT_ID      = "tenantId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest http) {
                String correlationId = http.getHeader("X-Correlation-ID");
                String userId        = http.getHeader("X-User-ID");
                String tenantId      = http.getHeader("X-Tenant-ID");

                if (correlationId != null) MDC.put(CORRELATION_ID, correlationId);
                if (userId        != null) MDC.put(USER_ID,        userId);
                if (tenantId      != null) MDC.put(TENANT_ID,      tenantId);
            }
            chain.doFilter(request, response);
        } finally {
            // Always clear MDC after request — threads are reused from pool
            MDC.remove(CORRELATION_ID);
            MDC.remove(USER_ID);
            MDC.remove(TENANT_ID);
        }
    }
}

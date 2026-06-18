package com.marketly.order.config;

import com.marketly.common.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final EntityManager entityManager;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantFilterInterceptor(entityManager));
    }

    @RequiredArgsConstructor
    @Slf4j
    private static class TenantFilterInterceptor implements HandlerInterceptor {

        private final EntityManager entityManager;

        @Override
        public boolean preHandle(@NonNull HttpServletRequest request,
                                 @NonNull HttpServletResponse response,
                                 @NonNull Object handler) {
            String header = request.getHeader("X-Tenant-ID");
            if (header != null && !header.isBlank()) {
                try {
                    UUID tenantId = UUID.fromString(header);
                    TenantContext.set(tenantId);
                    Session session = entityManager.unwrap(Session.class);
                    Filter filter = session.enableFilter("tenantFilter");
                    filter.setParameter("tenantId", tenantId);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid X-Tenant-ID header: '{}'", header);
                }
            }
            return true;
        }

        @Override
        public void afterCompletion(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull Object handler, Exception ex) {
            TenantContext.clear();
        }
    }
}

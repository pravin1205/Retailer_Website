package com.marketly.tenant.service;

import com.marketly.common.dto.PageResponse;
import com.marketly.common.exception.DuplicateResourceException;
import com.marketly.common.exception.ResourceNotFoundException;
import com.marketly.tenant.dto.CreateTenantRequest;
import com.marketly.tenant.entity.Tenant;
import com.marketly.tenant.entity.TenantDomain;
import com.marketly.tenant.entity.TenantSetting;
import com.marketly.tenant.event.TenantEventProducer;
import com.marketly.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantEventProducer tenantEventProducer;

    @Transactional
    public Tenant createTenant(CreateTenantRequest request, UUID requestingUserId) {
        if (tenantRepository.existsBySlugAndDeletedAtIsNull(request.getSlug())) {
            throw new DuplicateResourceException(
                "Tenant with slug '" + request.getSlug() + "' already exists.");
        }

        Tenant tenant = Tenant.builder()
            .slug(request.getSlug())
            .name(request.getName())
            .tagline(request.getTagline())
            .description(request.getDescription())
            .category(request.getCategory())
            .ownerUserId(requestingUserId)
            .subscriptionPlan(request.getSubscriptionPlan() != null ? request.getSubscriptionPlan() : "FREE")
            .status("PENDING")
            .accentColor(request.getAccentColor())
            .logoUrl(request.getLogoUrl())
            .build();

        // Add default subdomain
        TenantDomain subdomain = TenantDomain.builder()
            .tenant(tenant)
            .domain(request.getSlug() + ".marketly.com")
            .primary(true)
            .verified(true)
            .build();
        tenant.getDomains().add(subdomain);

        tenant = tenantRepository.save(tenant);
        log.info("Tenant created: {} ({})", tenant.getSlug(), tenant.getId());
        tenantEventProducer.publishTenantCreated(tenant, request.getOwnerEmail());
        return tenant;
    }

    @Cacheable(value = "tenant-config", key = "#slug")
    @Transactional(readOnly = true)
    public Tenant getBySlug(String slug) {
        return tenantRepository.findBySlugAndDeletedAtIsNull(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", slug));
    }

    @Transactional(readOnly = true)
    public PageResponse<Tenant> listTenants(String search, String status,
                                            String category, int page, int size) {
        PageRequest pageable = PageRequest.of(
            page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Tenant> result = tenantRepository.findAllFiltered(search, status, category, pageable);
        return PageResponse.of(result);
    }

    @CacheEvict(value = "tenant-config", key = "#slug")
    @Transactional
    public Tenant updateTenant(String slug, Map<String, Object> updates, UUID requestingUserId) {
        Tenant tenant = tenantRepository.findBySlugAndDeletedAtIsNull(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", slug));

        if (updates.containsKey("name"))        tenant.setName((String) updates.get("name"));
        if (updates.containsKey("tagline"))     tenant.setTagline((String) updates.get("tagline"));
        if (updates.containsKey("description")) tenant.setDescription((String) updates.get("description"));
        if (updates.containsKey("logoUrl"))     tenant.setLogoUrl((String) updates.get("logoUrl"));
        if (updates.containsKey("bannerUrl"))   tenant.setBannerUrl((String) updates.get("bannerUrl"));
        if (updates.containsKey("accentColor")) tenant.setAccentColor((String) updates.get("accentColor"));

        return tenantRepository.save(tenant);
    }

    @CacheEvict(value = "tenant-config", key = "#slug")
    @Transactional
    public Tenant updateStatus(String slug, String status) {
        Tenant tenant = tenantRepository.findBySlugAndDeletedAtIsNull(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", slug));
        tenant.setStatus(status);
        Tenant saved = tenantRepository.save(tenant);
        if ("ACTIVE".equals(status)) {
            tenantEventProducer.publishTenantActivated(saved);
        }
        return saved;
    }

    @CacheEvict(value = "tenant-config", key = "#slug")
    @Transactional
    public void updateSettings(String slug, Map<String, String> settings) {
        Tenant tenant = tenantRepository.findBySlugAndDeletedAtIsNull(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", slug));

        settings.forEach((key, value) -> {
            TenantSetting existing = tenant.getSettings().stream()
                .filter(s -> s.getKey().equals(key))
                .findFirst()
                .orElse(null);
            if (existing != null) {
                existing.setValue(value);
            } else {
                TenantSetting newSetting = TenantSetting.builder()
                    .tenant(tenant)
                    .key(key)
                    .value(value)
                    .build();
                tenant.getSettings().add(newSetting);
            }
        });
        tenantRepository.save(tenant);
    }
}

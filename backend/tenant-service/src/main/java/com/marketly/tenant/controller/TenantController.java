package com.marketly.tenant.controller;

import com.marketly.common.dto.ApiResponse;
import com.marketly.common.dto.PageResponse;
import com.marketly.common.exception.UnauthorizedException;
import com.marketly.tenant.dto.CreateTenantRequest;
import com.marketly.tenant.entity.Tenant;
import com.marketly.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Tenant management — onboarding, config, status")
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @Operation(summary = "List all tenants with filtering and pagination")
    public ResponseEntity<ApiResponse<PageResponse<Tenant>>> listTenants(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<Tenant> result = tenantService.listTenants(search, status, category, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    @Operation(summary = "Onboard a new tenant")
    public ResponseEntity<ApiResponse<Tenant>> createTenant(
            @Valid @RequestBody CreateTenantRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        UUID requestingUserId = userId != null ? UUID.fromString(userId) : UUID.randomUUID();
        Tenant tenant = tenantService.createTenant(request, requestingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tenant));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get tenant by slug")
    public ResponseEntity<ApiResponse<Tenant>> getTenant(@PathVariable String slug) {
        Tenant tenant = tenantService.getBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(tenant));
    }

    @GetMapping("/{slug}/detail")
    @Operation(summary = "Get full tenant detail including all settings (admin use)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTenantDetail(@PathVariable String slug) {
        Tenant tenant = tenantService.getBySlugWithSettings(slug);
        // Flatten settings into a map
        Map<String, String> settings = new java.util.LinkedHashMap<>();
        tenant.getSettings().forEach(s -> settings.put(s.getKey(), s.getValue()));

        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("id",               tenant.getId());
        detail.put("slug",             tenant.getSlug());
        detail.put("name",             tenant.getName());
        detail.put("tagline",          tenant.getTagline());
        detail.put("description",      tenant.getDescription());
        detail.put("category",         tenant.getCategory());
        detail.put("status",           tenant.getStatus());
        detail.put("onboardingStep",   tenant.getOnboardingStep());
        detail.put("ownerUserId",      tenant.getOwnerUserId());
        detail.put("subscriptionPlan", tenant.getSubscriptionPlan());
        detail.put("logoUrl",          tenant.getLogoUrl());
        detail.put("bannerUrl",        tenant.getBannerUrl());
        detail.put("accentColor",      tenant.getAccentColor());
        detail.put("createdAt",        tenant.getCreatedAt());
        detail.put("settings",         settings);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @PutMapping("/{slug}")
    @Operation(summary = "Update tenant profile")
    public ResponseEntity<ApiResponse<Tenant>> updateTenant(
            @PathVariable String slug,
            @RequestBody Map<String, Object> updates,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        UUID requestingUserId = userId != null ? UUID.fromString(userId) : null;
        Tenant tenant = tenantService.updateTenant(slug, updates, requestingUserId);
        return ResponseEntity.ok(ApiResponse.success(tenant));
    }

    @PatchMapping("/{slug}/settings")
    @Operation(summary = "Update tenant settings (key-value pairs)")
    public ResponseEntity<ApiResponse<Void>> updateSettings(
            @PathVariable String slug,
            @RequestBody Map<String, String> settings) {
        tenantService.updateSettings(slug, settings);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{slug}/status")
    @Operation(summary = "Change tenant status (SUPER_ADMIN only)")
    public ResponseEntity<ApiResponse<Tenant>> updateStatus(
            @PathVariable String slug,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        requireSuperAdmin(roles);
        Tenant tenant = tenantService.updateStatus(slug, body.get("status"));
        return ResponseEntity.ok(ApiResponse.success(tenant));
    }

    // ── Access control helper ───────────────────────────────────────────────

    private void requireSuperAdmin(String rolesHeader) {
        if (rolesHeader == null || !java.util.Arrays.asList(rolesHeader.split(",")).contains("SUPER_ADMIN")) {
            throw new UnauthorizedException("This action requires SUPER_ADMIN role.");
        }
    }
}

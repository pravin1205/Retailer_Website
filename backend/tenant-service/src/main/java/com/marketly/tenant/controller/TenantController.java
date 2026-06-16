package com.marketly.tenant.controller;

import com.marketly.common.dto.ApiResponse;
import com.marketly.common.dto.PageResponse;
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
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
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
            @RequestBody Map<String, String> body) {
        Tenant tenant = tenantService.updateStatus(slug, body.get("status"));
        return ResponseEntity.ok(ApiResponse.success(tenant));
    }
}

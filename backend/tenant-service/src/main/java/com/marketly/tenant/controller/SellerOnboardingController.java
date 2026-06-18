package com.marketly.tenant.controller;

import com.marketly.common.dto.ApiResponse;
import com.marketly.common.exception.BusinessException;
import com.marketly.common.exception.ResourceNotFoundException;
import com.marketly.common.exception.UnauthorizedException;
import com.marketly.tenant.dto.KycRequest;
import com.marketly.tenant.entity.Tenant;
import com.marketly.tenant.entity.TenantSetting;
import com.marketly.tenant.event.TenantEventProducer;
import com.marketly.tenant.repository.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Seller Onboarding", description = "KYC submission and admin approval workflow")
public class SellerOnboardingController {

    private final TenantRepository    tenantRepository;
    private final TenantEventProducer tenantEventProducer;

    // ── KYC Submission ────────────────────────────────────────────────────────

    @PostMapping("/{slug}/kyc")
    @Operation(summary = "Submit KYC documents for a seller tenant")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitKyc(
            @PathVariable String slug,
            @Valid @RequestBody KycRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {

        Tenant tenant = tenantRepository.findBySlugAndDeletedAtIsNull(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", slug));

        // Only owner can submit KYC
        if (userId != null) {
            UUID ownerUserId = tenant.getOwnerUserId();
            if (!ownerUserId.toString().equals(userId)) {
                throw new BusinessException("FORBIDDEN",
                    "Only the store owner can submit KYC.", HttpStatus.FORBIDDEN);
            }
        }

        // Store KYC fields as TenantSettings (key-value — no new table needed)
        upsertSetting(tenant, "kyc_aadhaar",       request.getAadhaarNumber());
        upsertSetting(tenant, "kyc_pan",            request.getPanNumber());
        upsertSetting(tenant, "kyc_gst",            request.getGstNumber() != null ? request.getGstNumber() : "");
        upsertSetting(tenant, "kyc_store_image",    request.getStoreImageUrl() != null ? request.getStoreImageUrl() : "");
        upsertSetting(tenant, "kyc_status",         "SUBMITTED");

        if (request.getDocumentUrls() != null && !request.getDocumentUrls().isEmpty()) {
            upsertSetting(tenant, "kyc_document_urls", String.join(",", request.getDocumentUrls()));
        }

        // Advance tenant status and onboarding step
        tenant.setStatus("PENDING_VERIFICATION");
        tenant.setOnboardingStep("KYC_SUBMITTED");
        Tenant saved = tenantRepository.save(tenant);

        // Resolve owner email from settings if available
        String ownerEmail = getSettingValue(saved, "business_email");
        tenantEventProducer.publishKycSubmitted(saved, ownerEmail);

        log.info("KYC submitted for tenant={}", slug);

        Map<String, Object> data = Map.of(
            "slug",           saved.getSlug(),
            "status",         saved.getStatus(),
            "onboardingStep", saved.getOnboardingStep(),
            "message",        "KYC submitted successfully. Review takes 1-2 business days."
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ── Mark Under Review ─────────────────────────────────────────────────────

    @PatchMapping("/{slug}/review")
    @Operation(summary = "Mark a seller application as UNDER_REVIEW (SUPER_ADMIN only)")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> markUnderReview(
            @PathVariable String slug,
            @RequestHeader(value = "X-User-ID", required = false) String adminUserId,
            @RequestHeader(value = "X-Roles", required = false) String roles) {

        requireSuperAdmin(roles);

        Tenant tenant = tenantRepository.findBySlugAndDeletedAtIsNull(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", slug));

        if (!"PENDING_VERIFICATION".equals(tenant.getStatus()) &&
            !"PENDING".equals(tenant.getStatus())) {
            throw new BusinessException("INVALID_TRANSITION",
                "Can only move to UNDER_REVIEW from PENDING_VERIFICATION.", HttpStatus.BAD_REQUEST);
        }

        tenant.setStatus("UNDER_REVIEW");
        tenantRepository.save(tenant);

        log.info("Tenant marked UNDER_REVIEW: slug={} by adminUserId={}", slug, adminUserId);

        Map<String, Object> data = Map.of(
            "slug",   tenant.getSlug(),
            "status", "UNDER_REVIEW"
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ── Admin Approval / Rejection ────────────────────────────────────────────

    @PatchMapping("/{slug}/approve")
    @Operation(summary = "Approve or reject a seller KYC (SUPER_ADMIN only)")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveTenant(
            @PathVariable String slug,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-ID", required = false) String adminUserId,
            @RequestHeader(value = "X-Roles", required = false) String roles) {

        requireSuperAdmin(roles);

        Tenant tenant = tenantRepository.findBySlugAndDeletedAtIsNull(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", slug));

        String action      = body.getOrDefault("action", "").toUpperCase();
        String reviewNotes = body.getOrDefault("reviewNotes", "");

        String ownerEmail = getSettingValue(tenant, "business_email");

        switch (action) {
            case "APPROVE" -> {
                tenant.setStatus("ACTIVE");
                tenant.setOnboardingStep("COMPLETE");
                upsertSetting(tenant, "kyc_status", "APPROVED");
                if (reviewNotes != null && !reviewNotes.isBlank()) {
                    upsertSetting(tenant, "kyc_review_notes", reviewNotes);
                }
                Tenant saved = tenantRepository.save(tenant);

                tenantEventProducer.publishSellerApproved(saved, ownerEmail);
                tenantEventProducer.publishTenantActivated(saved);

                log.info("Tenant approved: slug={} by adminUserId={}", slug, adminUserId);
            }
            case "REJECT" -> {
                tenant.setStatus("REJECTED");
                tenant.setOnboardingStep("KYC_REJECTED");
                upsertSetting(tenant, "kyc_status", "REJECTED");
                upsertSetting(tenant, "kyc_review_notes", reviewNotes);
                tenantRepository.save(tenant);

                tenantEventProducer.publishSellerRejected(tenant, ownerEmail, reviewNotes);

                log.info("Tenant rejected: slug={} reason={}", slug, reviewNotes);
            }
            default -> throw new BusinessException("INVALID_ACTION",
                "Action must be APPROVE or REJECT.", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> data = Map.of(
            "slug",   tenant.getSlug(),
            "status", tenant.getStatus(),
            "action", action
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // ── Access control helper ─────────────────────────────────────────────────

    private void requireSuperAdmin(String rolesHeader) {
        if (rolesHeader == null || !java.util.Arrays.asList(rolesHeader.split(",")).contains("SUPER_ADMIN")) {
            throw new UnauthorizedException("This action requires SUPER_ADMIN role.");
        }
    }

    private void upsertSetting(Tenant tenant, String key, String value) {
        if (value == null) return;
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
    }

    private String getSettingValue(Tenant tenant, String key) {
        return tenant.getSettings().stream()
            .filter(s -> s.getKey().equals(key))
            .map(TenantSetting::getValue)
            .findFirst()
            .orElse("");
    }
}

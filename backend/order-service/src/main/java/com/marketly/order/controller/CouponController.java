package com.marketly.order.controller;

import com.marketly.common.dto.ApiResponse;
import com.marketly.order.entity.Coupon;
import com.marketly.order.repository.CouponRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupons", description = "Coupon management")
public class CouponController {

    private final CouponRepository couponRepository;

    @GetMapping
    @Operation(summary = "List active coupons for this tenant")
    public ResponseEntity<ApiResponse<List<Coupon>>> listCoupons(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Coupon> coupons = couponRepository
            .findByTenantIdAndActiveAndDeletedAtIsNull(UUID.fromString(tenantId), true);
        return ResponseEntity.ok(ApiResponse.success(coupons));
    }

    @PostMapping
    @Operation(summary = "Create a coupon (owner/manager only)")
    public ResponseEntity<ApiResponse<Coupon>> createCoupon(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID tid = UUID.fromString(tenantId);

        Coupon coupon = Coupon.builder()
            .code(((String) body.get("code")).toUpperCase())
            .description((String) body.get("description"))
            .type((String) body.get("type"))
            .value(new BigDecimal(body.get("value").toString()))
            .minOrderValue(body.get("minOrderValue") != null
                ? new BigDecimal(body.get("minOrderValue").toString()) : null)
            .maxDiscount(body.get("maxDiscount") != null
                ? new BigDecimal(body.get("maxDiscount").toString()) : null)
            .usageLimit(body.get("usageLimit") != null
                ? Integer.parseInt(body.get("usageLimit").toString()) : null)
            .perUserLimit(body.get("perUserLimit") != null
                ? Integer.parseInt(body.get("perUserLimit").toString()) : 1)
            .validFrom(body.get("validFrom") != null
                ? Instant.parse((String) body.get("validFrom")) : Instant.now())
            .validUntil(body.get("validUntil") != null
                ? Instant.parse((String) body.get("validUntil")) : null)
            .active(true)
            .build();
        coupon.setTenantId(tid);

        Coupon saved = couponRepository.save(coupon);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(saved));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Deactivate or update a coupon")
    public ResponseEntity<ApiResponse<Coupon>> updateCoupon(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        Coupon coupon = couponRepository.findById(id)
            .filter(c -> c.getTenantId().equals(UUID.fromString(tenantId)))
            .orElseThrow(() -> new com.marketly.common.exception.ResourceNotFoundException("Coupon", id.toString()));

        if (body.containsKey("isActive"))
            coupon.setActive(Boolean.parseBoolean(body.get("isActive").toString()));
        if (body.containsKey("validUntil"))
            coupon.setValidUntil(Instant.parse((String) body.get("validUntil")));

        return ResponseEntity.ok(ApiResponse.success(couponRepository.save(coupon)));
    }
}

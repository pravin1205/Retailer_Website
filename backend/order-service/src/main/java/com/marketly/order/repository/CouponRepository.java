package com.marketly.order.repository;

import com.marketly.order.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByTenantIdAndCodeAndDeletedAtIsNull(UUID tenantId, String code);

    List<Coupon> findByTenantIdAndActiveAndDeletedAtIsNull(UUID tenantId, boolean active);
}

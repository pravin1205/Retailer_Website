package com.marketly.order.entity;

import com.marketly.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "coupons",
    schema = "order_data",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_coupon_tenant_code",
        columnNames = {"tenant_id", "code"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "type", nullable = false, length = 20)
    private String type;  // PERCENT | FLAT | FREE_DELIVERY

    @Column(name = "value", nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "min_order_value", precision = 12, scale = 2)
    private BigDecimal minOrderValue;

    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private int usedCount = 0;

    @Column(name = "per_user_limit", nullable = false)
    @Builder.Default
    private int perUserLimit = 1;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    public boolean isCurrentlyValid() {
        Instant now = Instant.now();
        if (!active) return false;
        if (now.isBefore(validFrom)) return false;
        if (validUntil != null && now.isAfter(validUntil)) return false;
        if (usageLimit != null && usedCount >= usageLimit) return false;
        return true;
    }

    public BigDecimal calculateDiscount(BigDecimal orderSubtotal) {
        if ("PERCENT".equals(type)) {
            BigDecimal discount = orderSubtotal.multiply(value)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            if (maxDiscount != null) {
                discount = discount.min(maxDiscount);
            }
            return discount;
        } else if ("FLAT".equals(type)) {
            return value.min(orderSubtotal);
        }
        return BigDecimal.ZERO;
    }
}

package com.marketly.customer.entity;

import com.marketly.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Customer profile scoped to a tenant.
 * One user_id can have customer records at multiple tenants.
 * The unique constraint (tenant_id, user_id) enforces exactly one
 * profile per store per user.
 */
@Entity
@Table(
    name = "customers",
    schema = "customer",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_customer_tenant_user",
        columnNames = {"tenant_id", "user_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends TenantAwareEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "loyalty_points", nullable = false)
    @Builder.Default
    private int loyaltyPoints = 0;

    @Column(name = "tier", nullable = false, length = 30)
    @Builder.Default
    private String tier = "BRONZE";   // BRONZE, SILVER, GOLD, PLATINUM

    @Column(name = "total_orders", nullable = false)
    @Builder.Default
    private int totalOrders = 0;

    @Column(name = "total_spent", nullable = false)
    @Builder.Default
    private java.math.BigDecimal totalSpent = java.math.BigDecimal.ZERO;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Address> addresses = new ArrayList<>();

    public void addLoyaltyPoints(int points) {
        this.loyaltyPoints += points;
        recalculateTier();
    }

    public void recordOrder(java.math.BigDecimal orderTotal) {
        this.totalOrders++;
        this.totalSpent = this.totalSpent.add(orderTotal);
        recalculateTier();
    }

    private void recalculateTier() {
        if (loyaltyPoints >= 5000 || totalOrders >= 50) {
            this.tier = "PLATINUM";
        } else if (loyaltyPoints >= 1000 || totalOrders >= 20) {
            this.tier = "GOLD";
        } else if (loyaltyPoints >= 200 || totalOrders >= 5) {
            this.tier = "SILVER";
        } else {
            this.tier = "BRONZE";
        }
    }
}

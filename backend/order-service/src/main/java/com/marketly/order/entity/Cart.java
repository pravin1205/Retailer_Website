package com.marketly.order.entity;

import com.marketly.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts", schema = "order_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart extends TenantAwareEntity {

    /** NULL for guest carts */
    @Column(name = "customer_id")
    private UUID customerId;

    /** For guest carts before login */
    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";  // ACTIVE | CONVERTED | ABANDONED

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    public int getTotalItemCount() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}

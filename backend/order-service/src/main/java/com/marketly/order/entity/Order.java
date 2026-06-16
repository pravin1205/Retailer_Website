package com.marketly.order.entity;

import com.marketly.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "orders",
    schema = "order_data",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_order_number",
        columnNames = {"tenant_id", "order_number"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends TenantAwareEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_number", nullable = false, length = 30)
    private String orderNumber;

    /**
     * Order lifecycle:
     * PLACED → CONFIRMED → PACKING → OUT_FOR_DELIVERY → DELIVERED
     *                    ↘ CANCELLED
     * DELIVERED → RETURN_REQUESTED → RETURNED
     */
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "PLACED";

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "delivery_charge", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal deliveryCharge = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "delivery_slot", length = 100)
    private String deliverySlot;

    /** Snapshot of the delivery address at order time */
    @Column(name = "delivery_address", columnDefinition = "jsonb")
    private String deliveryAddress;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;  // COD | UPI | CARD | WALLET

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "placed_at", nullable = false)
    @Builder.Default
    private Instant placedAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Payment> payments = new ArrayList<>();
}

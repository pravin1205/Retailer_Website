package com.marketly.analytics.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Aggregated daily order summary per tenant.
 * One row per (tenant_id, summary_date).
 * Updated in real-time by the Kafka consumer.
 *
 * Powers the dashboard metrics: revenue today, orders today, AOV, etc.
 * Avoids running expensive GROUP BY queries on the orders table.
 */
@Entity
@Table(
    name = "order_summaries",
    schema = "analytics",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_order_summary",
        columnNames = {"tenant_id", "summary_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "total_orders", nullable = false)
    @Builder.Default
    private int totalOrders = 0;

    @Column(name = "completed_orders", nullable = false)
    @Builder.Default
    private int completedOrders = 0;

    @Column(name = "cancelled_orders", nullable = false)
    @Builder.Default
    private int cancelledOrders = 0;

    @Column(name = "gross_revenue", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal grossRevenue = BigDecimal.ZERO;

    @Column(name = "net_revenue", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal netRevenue = BigDecimal.ZERO;

    @Column(name = "total_discount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    @Column(name = "new_customers", nullable = false)
    @Builder.Default
    private int newCustomers = 0;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private java.time.Instant updatedAt = java.time.Instant.now();

    public BigDecimal getAverageOrderValue() {
        if (totalOrders == 0) return BigDecimal.ZERO;
        return grossRevenue.divide(
            BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP);
    }
}

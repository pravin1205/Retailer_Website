package com.marketly.product.entity;

import com.marketly.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "inventory",
    schema = "product",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_inventory",
        columnNames = {"tenant_id", "product_id", "warehouse_code"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory extends TenantAwareEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "warehouse_code", nullable = false, length = 100)
    @Builder.Default
    private String warehouseCode = "DEFAULT";

    @Column(name = "quantity_on_hand", nullable = false)
    @Builder.Default
    private int quantityOnHand = 0;

    @Column(name = "quantity_reserved", nullable = false)
    @Builder.Default
    private int quantityReserved = 0;

    @Column(name = "low_stock_threshold", nullable = false)
    @Builder.Default
    private int lowStockThreshold = 5;

    @Column(name = "reorder_point", nullable = false)
    @Builder.Default
    private int reorderPoint = 10;

    public int getQuantityAvailable() {
        return Math.max(0, quantityOnHand - quantityReserved);
    }

    public boolean isInStock() {
        return getQuantityAvailable() > 0;
    }

    public boolean isLowStock() {
        return quantityOnHand <= lowStockThreshold;
    }
}

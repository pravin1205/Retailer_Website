package com.marketly.order.entity;

import com.marketly.common.entity.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
    name = "cart_items",
    schema = "order_data",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_cart_item",
        columnNames = {"cart_id", "product_id", "variant_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    /** Snapshot of name at add-to-cart time — survives product renames */
    @Column(name = "product_name", nullable = false, length = 500)
    private String productName;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    /** Snapshot of price at add-to-cart time — customer sees what they agreed to */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}

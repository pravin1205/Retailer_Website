package com.marketly.product.repository;

import com.marketly.product.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductId(UUID productId);

    @Modifying
    @Query("UPDATE Inventory i SET i.quantityOnHand = i.quantityOnHand + :delta " +
           "WHERE i.product.id = :productId AND i.tenantId = :tenantId")
    int adjustStock(@Param("productId") UUID productId,
                    @Param("tenantId") UUID tenantId,
                    @Param("delta") int delta);
}

package com.marketly.order.repository;

import com.marketly.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Order> findByOrderNumberAndTenantId(String orderNumber, UUID tenantId);

    Page<Order> findByTenantIdAndCustomerIdOrderByPlacedAtDesc(
        UUID tenantId, UUID customerId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.tenantId = :tenantId " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:search IS NULL OR o.orderNumber LIKE CONCAT('%',:search,'%')) " +
           "ORDER BY o.placedAt DESC")
    Page<Order> findByTenantFiltered(
        @Param("tenantId") UUID tenantId,
        @Param("status")   String status,
        @Param("search")   String search,
        Pageable pageable
    );

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(o.orderNumber, LENGTH(o.orderNumber) - 3) AS int)), 0) " +
           "FROM Order o WHERE o.tenantId = :tenantId AND o.orderNumber LIKE CONCAT(:prefix, '%')")
    int findMaxSequenceForPrefix(@Param("tenantId") UUID tenantId,
                                 @Param("prefix") String prefix);
}

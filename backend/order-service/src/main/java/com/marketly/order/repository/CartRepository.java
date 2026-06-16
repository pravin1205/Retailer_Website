package com.marketly.order.repository;

import com.marketly.order.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByTenantIdAndCustomerIdAndStatus(
        UUID tenantId, UUID customerId, String status);

    Optional<Cart> findByTenantIdAndSessionIdAndStatus(
        UUID tenantId, String sessionId, String status);
}

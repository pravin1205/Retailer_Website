package com.marketly.customer.repository;

import com.marketly.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByTenantIdAndUserIdAndDeletedAtIsNull(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserIdAndDeletedAtIsNull(UUID tenantId, UUID userId);

    @Query("SELECT c FROM Customer c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL " +
           "AND (:search IS NULL OR LOWER(c.firstName) LIKE LOWER(CONCAT('%',:search,'%')) " +
           "  OR LOWER(c.lastName)  LIKE LOWER(CONCAT('%',:search,'%')) " +
           "  OR LOWER(c.email)     LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<Customer> findByTenantIdFiltered(
        @Param("tenantId") UUID tenantId,
        @Param("search") String search,
        Pageable pageable
    );
}

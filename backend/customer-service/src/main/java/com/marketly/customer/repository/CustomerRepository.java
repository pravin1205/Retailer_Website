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

    // Used when no search term — avoids null binding that confuses PostgreSQL type inference
    @Query("SELECT c FROM Customer c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL")
    Page<Customer> findByTenantId(
        @Param("tenantId") UUID tenantId,
        Pageable pageable
    );

    // Used when a search term is present — all parameters are non-null, no type ambiguity
    @Query("SELECT c FROM Customer c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL " +
           "AND (LOWER(c.firstName) LIKE LOWER(CONCAT('%',:search,'%')) " +
           "  OR LOWER(c.lastName)  LIKE LOWER(CONCAT('%',:search,'%')) " +
           "  OR LOWER(c.email)     LIKE LOWER(CONCAT('%',:search,'%')) " +
           "  OR c.phone            LIKE CONCAT('%',:search,'%'))")
    Page<Customer> findByTenantIdAndSearch(
        @Param("tenantId") UUID tenantId,
        @Param("search") String search,
        Pageable pageable
    );
}

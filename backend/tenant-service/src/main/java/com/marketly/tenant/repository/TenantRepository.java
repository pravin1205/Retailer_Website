package com.marketly.tenant.repository;

import com.marketly.tenant.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    @Query("SELECT t FROM Tenant t WHERE t.deletedAt IS NULL " +
           "AND (:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:category IS NULL OR t.category = :category)")
    Page<Tenant> findAllFiltered(
        @Param("search") String search,
        @Param("status") String status,
        @Param("category") String category,
        Pageable pageable
    );
}

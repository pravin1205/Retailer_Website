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

    @Query(
        value = "SELECT * FROM tenant.tenants t " +
                "WHERE t.deleted_at IS NULL " +
                "AND (CAST(:search AS text) IS NULL OR LOWER(t.name) LIKE LOWER('%' || CAST(:search AS text) || '%')) " +
                "AND (CAST(:status AS text) IS NULL OR t.status = CAST(:status AS text)) " +
                "AND (CAST(:category AS text) IS NULL OR t.category = CAST(:category AS text)) " +
                "ORDER BY t.created_at DESC",
        countQuery = "SELECT COUNT(*) FROM tenant.tenants t " +
                "WHERE t.deleted_at IS NULL " +
                "AND (CAST(:search AS text) IS NULL OR LOWER(t.name) LIKE LOWER('%' || CAST(:search AS text) || '%')) " +
                "AND (CAST(:status AS text) IS NULL OR t.status = CAST(:status AS text)) " +
                "AND (CAST(:category AS text) IS NULL OR t.category = CAST(:category AS text))",
        nativeQuery = true
    )
    Page<Tenant> findAllFiltered(
        @Param("search") String search,
        @Param("status") String status,
        @Param("category") String category,
        Pageable pageable
    );
}

package com.marketly.product.repository;

import com.marketly.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    boolean existsBySkuAndTenantIdAndDeletedAtIsNull(String sku, UUID tenantId);

    @Query("SELECT p FROM Product p " +
           "WHERE p.tenantId = :tenantId " +
           "AND p.deletedAt IS NULL " +
           "AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:isActive IS NULL OR p.active = :isActive) " +
           "AND (:isFeatured IS NULL OR p.featured = :isFeatured)")
    Page<Product> findByFilters(
        @Param("tenantId") UUID tenantId,
        @Param("search") String search,
        @Param("categoryId") UUID categoryId,
        @Param("isActive") Boolean isActive,
        @Param("isFeatured") Boolean isFeatured,
        Pageable pageable
    );
}

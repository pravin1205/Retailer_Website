package com.marketly.product.repository;

import com.marketly.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByTenantIdAndParentIsNullAndDeletedAtIsNull(UUID tenantId);

    List<Category> findByTenantIdAndParentIdAndDeletedAtIsNull(UUID tenantId, UUID parentId);

    Optional<Category> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    boolean existsBySlugAndTenantIdAndDeletedAtIsNull(String slug, UUID tenantId);
}

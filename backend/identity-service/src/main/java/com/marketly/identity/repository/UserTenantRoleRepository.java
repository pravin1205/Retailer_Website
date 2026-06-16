package com.marketly.identity.repository;

import com.marketly.identity.entity.UserTenantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserTenantRoleRepository extends JpaRepository<UserTenantRole, UUID> {

    @Query("SELECT utr FROM UserTenantRole utr WHERE utr.user.id = :userId AND utr.deletedAt IS NULL")
    List<UserTenantRole> findByUserIdAndDeletedAtIsNull(@Param("userId") UUID userId);

    @Query("SELECT utr FROM UserTenantRole utr WHERE utr.user.id = :userId AND utr.tenantId = :tenantId AND utr.deletedAt IS NULL")
    List<UserTenantRole> findByUserIdAndTenantIdAndDeletedAtIsNull(
        @Param("userId") UUID userId, @Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(utr) > 0 FROM UserTenantRole utr WHERE utr.user.id = :userId AND utr.tenantId = :tenantId AND utr.role.name = :roleName AND utr.deletedAt IS NULL")
    boolean existsByUserIdAndTenantIdAndRoleNameAndDeletedAtIsNull(
        @Param("userId") UUID userId,
        @Param("tenantId") UUID tenantId,
        @Param("roleName") String roleName);
}

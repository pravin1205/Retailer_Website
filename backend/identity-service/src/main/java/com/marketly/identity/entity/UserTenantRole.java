package com.marketly.identity.entity;

import com.marketly.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Maps a user to a role within a specific tenant.
 * A user can be CUSTOMER at tenant A and TENANT_OWNER at tenant B.
 */
@Entity
@Table(
    name = "user_tenant_roles",
    schema = "identity",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_user_tenant_role",
        columnNames = {"user_id", "tenant_id", "role_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTenantRole extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
}

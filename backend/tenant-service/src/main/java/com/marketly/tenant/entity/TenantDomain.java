package com.marketly.tenant.entity;

import com.marketly.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "tenant_domains",
    schema = "tenant",
    uniqueConstraints = @UniqueConstraint(name = "uq_tenant_domain", columnNames = "domain")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantDomain extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "domain", nullable = false, length = 253)
    private String domain;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean verified = false;
}

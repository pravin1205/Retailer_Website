package com.marketly.common.entity;

import com.marketly.common.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

/**
 * Extends AuditableEntity with tenant isolation.
 * All tenant-scoped entities must extend this class.
 *
 * The Hibernate @Filter is activated by TenantAwareRepositoryImpl
 * on every session, ensuring all queries are automatically scoped
 * to the current tenant.
 */
@Getter
@Setter
@MappedSuperclass
@FilterDef(
    name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = UUID.class)
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantAwareEntity extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @PrePersist
    void injectTenant() {
        if (this.tenantId == null) {
            UUID current = TenantContext.get();
            if (current != null) {
                this.tenantId = current;
            }
        }
    }
}

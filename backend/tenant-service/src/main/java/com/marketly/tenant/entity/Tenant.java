package com.marketly.tenant.entity;

import com.marketly.common.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "tenants",
    schema = "tenant",
    uniqueConstraints = @UniqueConstraint(name = "uq_tenants_slug", columnNames = "slug")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends AuditableEntity {

    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "tagline", length = 500)
    private String tagline;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "subscription_plan", nullable = false, length = 50)
    @Builder.Default
    private String subscriptionPlan = "FREE";

    @Column(name = "subscription_expires_at")
    private Instant subscriptionExpiresAt;

    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    @Column(name = "banner_url", length = 2048)
    private String bannerUrl;

    @Column(name = "accent_color", length = 30)
    private String accentColor;

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TenantDomain> domains = new ArrayList<>();

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TenantSetting> settings = new ArrayList<>();
}

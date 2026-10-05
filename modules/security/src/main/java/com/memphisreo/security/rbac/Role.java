package com.memphisreo.security.rbac;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Роль агенції (tenant-scoped, RLS). Дві built-in ролі (is_system_default = true)
 * заводяться при онбордингу tenant-а: TENANT_ADMIN, AGENT. Tenant admin може
 * створювати власні ролі поверх каталогу {@link Permission} через адмінку —
 * docs/security.md §3.
 */
@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_system_default", nullable = false)
    private boolean systemDefault;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

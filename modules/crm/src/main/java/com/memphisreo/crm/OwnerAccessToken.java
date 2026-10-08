package com.memphisreo.crm;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Доступ власника (контакту) до звіту по своїх об'єктах — посилання без пароля; у БД лише хеш. */
@Entity
@Table(name = "owner_access_token")
@Getter
@Setter
@NoArgsConstructor
public class OwnerAccessToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_by_agent_id", nullable = false)
    private UUID createdByAgentId;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

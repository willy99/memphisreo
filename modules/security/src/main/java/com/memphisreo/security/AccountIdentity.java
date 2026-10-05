package com.memphisreo.security;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Control-plane логін tenant-користувача (агента) — email → tenant_id → agent_id.
 * Профіль агента живе в tenant-схемі, це лише резолюція логіну. docs/domain-model.md §1.
 */
@Entity
@Table(name = "account_identity", schema = "control_plane")
@Getter
@Setter
@NoArgsConstructor
public class AccountIdentity {

    public enum Status { PENDING_INVITE, ACTIVE, DISABLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Nullable до прийняття запрошення (status=PENDING_INVITE) — docs/security.md §9. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    /** Інкремент = миттєвий logout усіх виданих access token — docs/security.md §4. */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = false;

    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    @Column(name = "two_factor_recovery_codes")
    private String twoFactorRecoveryCodes;

    @Column(name = "invite_token")
    private String inviteToken;

    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

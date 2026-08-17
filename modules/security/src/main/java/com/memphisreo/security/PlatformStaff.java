package com.memphisreo.security;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Логін співробітника Memphisreo (платформний адмін). Свідомо окрема таблиця
 * від {@link AccountIdentity} — бага в tenant-логіні не може дати платформний
 * доступ, бо спільного коду логіну немає. docs/security.md §2, §6.
 */
@Entity
@Table(name = "platform_staff", schema = "control_plane")
@Getter
@Setter
@NoArgsConstructor
public class PlatformStaff {

    public enum Status { ACTIVE, DISABLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

package com.memphisreo.activity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only подія таймлайну: що сталося з об'єктом/клієнтом, хто й коли.
 * Текст події формує фронт з {@code type} і {@code payload} (i18n), не бекенд.
 */
@Entity
@Table(name = "activity_event")
@Getter
@Setter
@NoArgsConstructor
public class ActivityEvent {

    public enum SubjectType { PROPERTY, CLIENT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(nullable = false, length = 40)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload = Map.of();

    @Column(name = "actor_agent_id")
    private UUID actorAgentId;

    @Column(name = "owner_visible", nullable = false)
    private boolean ownerVisible = true;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

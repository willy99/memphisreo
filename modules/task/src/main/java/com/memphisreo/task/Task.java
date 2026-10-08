package com.memphisreo.task;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Задача агента з дедлайном. Показ — теж задача (kind=SHOWING, прив'язана до showing_id). */
@Entity
@Table(name = "task")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    public enum Kind { SHOWING, FEEDBACK, FOLLOW_UP, CUSTOM }

    public enum Status { OPEN, DONE, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind = Kind.CUSTOM;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "assignee_agent_id", nullable = false)
    private UUID assigneeAgentId;

    @Column(name = "property_id")
    private UUID propertyId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "showing_id")
    private UUID showingId;

    private String note;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_by_agent_id")
    private UUID createdByAgentId;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

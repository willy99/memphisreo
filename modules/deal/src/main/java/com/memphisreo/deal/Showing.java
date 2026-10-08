package com.memphisreo.deal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Показ об'єкта клієнту(ам). property_id/agent_id — сирі посилання через межі модулів. */
@Entity
@Table(name = "showing")
@Getter
@Setter
@NoArgsConstructor
public class Showing {

    public enum Status { SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW }

    /** Головне заперечення після показу. */
    public enum Objection { PRICE, CONDITION, LOCATION, LAYOUT, OTHER }

    public enum NextStep { REPEAT_SHOWING, WAITING, OFFER, NOT_SUITABLE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 45;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.SCHEDULED;

    private String notes;

    @Column(name = "cancel_reason")
    private String cancelReason;

    private Integer interest;

    @Enumerated(EnumType.STRING)
    private Objection objection;

    @Column(name = "feedback_comment")
    private String feedbackComment;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_step")
    private NextStep nextStep;

    @Column(name = "feedback_at")
    private Instant feedbackAt;

    @Column(name = "created_by_agent_id", nullable = false)
    private UUID createdByAgentId;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Instant endsAt() {
        return scheduledAt.plusSeconds(durationMinutes * 60L);
    }

    public boolean isActive() {
        return status == Status.SCHEDULED || status == Status.CONFIRMED;
    }
}

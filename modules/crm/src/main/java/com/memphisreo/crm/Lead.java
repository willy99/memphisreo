package com.memphisreo.crm;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** listing_id/assigned_agent_id — сирі посилання через межу модуля. */
@Entity
@Table(name = "lead")
@Getter
@Setter
@NoArgsConstructor
public class Lead {

    public enum Status { NEW, CONTACTED, QUALIFIED, VIEWING_SCHEDULED, NEGOTIATING, WON, LOST }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "listing_id")
    private UUID listingId;

    @Column(name = "assigned_agent_id", nullable = false)
    private UUID assignedAgentId;

    @Column(name = "source_inquiry_id")
    private UUID sourceInquiryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.NEW;

    @Column(name = "next_follow_up_at")
    private Instant nextFollowUpAt;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}

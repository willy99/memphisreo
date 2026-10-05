package com.memphisreo.inquiry;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Фаза 2 перетворює це на повноцінний Lead — docs/domain-model.md §3. */
@Entity
@Table(name = "inquiry")
@Getter
@Setter
@NoArgsConstructor
public class Inquiry {

    public enum Status { NEW, CONTACTED, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    /** Денормалізовано на момент заявки. */
    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.NEW;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Захист від подвійної конвертації в кілька лідів — docs/domain-model.md §5. */
    @Column(name = "converted_to_lead_id")
    private UUID convertedToLeadId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

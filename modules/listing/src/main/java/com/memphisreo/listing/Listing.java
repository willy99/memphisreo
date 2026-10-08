package com.memphisreo.listing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** property_id/agent_id — сирі посилання через межу модуля — docs/domain-model.md §3. */
@Entity
@Table(name = "listing")
@Getter
@Setter
@NoArgsConstructor
public class Listing {

    public enum DealType { SALE, LONG_TERM_RENT, SHORT_TERM_RENT }

    /** Життєвий цикл продажу — docs/domain-model.md §6.0. */
    public enum Status { DRAFT, ACTIVE, UNDER_OFFER, SOLD, WITHDRAWN, EXPIRED }

    public enum MandateType { EXCLUSIVE, NON_EXCLUSIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "deal_type", nullable = false)
    private DealType dealType;

    /** Null у чернетці — ціну ще не вказали. */
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Власник — контакт агенції (сире посилання на модуль crm). */
    @Column(name = "seller_client_id")
    private UUID sellerClientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mandate_type")
    private MandateType mandateType;

    @Column(name = "mandate_valid_until")
    private LocalDate mandateValidUntil;

    @Column(name = "commission_percent")
    private BigDecimal commissionPercent;

    @Column(name = "commission_fixed")
    private BigDecimal commissionFixed;

    @Column(name = "access_notes")
    private String accessNotes;

    @Column(name = "hide_exact_address", nullable = false)
    private boolean hideExactAddress;

    @Column(name = "withdrawn_reason")
    private String withdrawnReason;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}

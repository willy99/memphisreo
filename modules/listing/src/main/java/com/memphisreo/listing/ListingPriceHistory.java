package com.memphisreo.listing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listing_price_history")
@Getter
@Setter
@NoArgsConstructor
public class ListingPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    /** Заповнює Hibernate з поточного tenant-а сесії — ADR-001. */
    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
}

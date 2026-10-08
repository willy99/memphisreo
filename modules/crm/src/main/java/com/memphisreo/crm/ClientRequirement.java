package com.memphisreo.crm;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Запит клієнта: що шукає. Коди типу/ринку/зручностей — рядки, щоб модуль crm
 * не залежав від property (підбір робить композиційний корінь).
 */
@Entity
@Table(name = "client_requirement")
@Getter
@Setter
@NoArgsConstructor
public class ClientRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "property_type")
    private String propertyType;

    @Column(name = "rooms_min")
    private Integer roomsMin;

    @Column(name = "rooms_max")
    private Integer roomsMax;

    @Column(name = "price_min")
    private BigDecimal priceMin;

    @Column(name = "price_max")
    private BigDecimal priceMax;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "area_min")
    private BigDecimal areaMin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> districts = new ArrayList<>();

    private String market;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "must_have", columnDefinition = "jsonb", nullable = false)
    private List<String> mustHave = new ArrayList<>();

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

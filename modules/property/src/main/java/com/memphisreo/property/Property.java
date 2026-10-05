package com.memphisreo.property;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** docs/domain-model.md §2. */
@Entity
@Table(name = "property")
@Getter
@Setter
@NoArgsConstructor
public class Property {

    public enum Type { APARTMENT, HOUSE, LAND, COMMERCIAL, OTHER }

    public enum Status { DRAFT, ACTIVE, RESERVED, SOLD, RENTED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    /** Квартира/офіс № у межах будівлі — nullable для будинку/ділянки. */
    @Column(name = "unit_number")
    private String unitNumber;

    @Column(name = "area_sqm", nullable = false)
    private BigDecimal areaSqm;

    /** Площа ділянки — окремо від area_sqm (площі будівлі), для house/land. */
    @Column(name = "land_area_sqm")
    private BigDecimal landAreaSqm;

    private Integer rooms;
    private Integer bedrooms;
    private Integer bathrooms;
    private Integer floor;

    @Column(name = "total_floors")
    private Integer totalFloors;

    @Column(name = "year_built")
    private Integer yearBuilt;

    @Column(name = "has_elevator")
    private Boolean hasElevator;

    @Column(name = "parking_spaces")
    private Integer parkingSpaces;

    /** Вільний текст для публічного пошуку — docs/architecture.md §8. */
    @Column(columnDefinition = "text")
    private String description;

    /** Per-country/per-type поля, валідуються проти Country.requiredPropertyFields — docs/domain-model.md §4. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private String attributesJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "created_by_agent_id", nullable = false)
    private UUID createdByAgentId;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}

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
import java.util.ArrayList;
import java.util.List;
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

    /** Вторинний ринок / новобудова (DIM.RIA realty_sale_type). */
    public enum Market { SECONDARY, NEW_BUILD }

    /** Матеріал стін — скорочений до найуживаніших перелік DIM.RIA wall_type. */
    public enum WallMaterial {
        BRICK, PANEL, MONOLITH, MONOLITH_BRICK, MONOLITH_FRAME, AERATED_CONCRETE, FOAM_BLOCK,
        SHELL_ROCK, CERAMIC_BLOCK, WOOD, FRAME, SIP, OTHER
    }

    /** Стан / ремонт (DIM.RIA flat_state, house_state; OpenImmo zustand). */
    public enum Condition { DESIGNER, EURO, GOOD, COSMETIC, NEEDS_RENOVATION, WHITE_BOX, SHELL }

    public enum Heating { CENTRAL, INDIVIDUAL_GAS, INDIVIDUAL_ELECTRIC, AUTONOMOUS, SOLID_FUEL, HEAT_PUMP, NONE }

    /** Цільове призначення ділянки (DIM.RIA land_use_purpose). */
    public enum LandPurpose {
        RESIDENTIAL, GARDENING, PERSONAL_FARMING, AGRICULTURAL, COMMERCIAL, INDUSTRIAL, RECREATIONAL, OTHER
    }

    public enum CommercialType { OFFICE, RETAIL, WAREHOUSE, INDUSTRIAL, HOSPITALITY, FREE_PURPOSE, BUILDING, OTHER }

    /** Зручності й комунікації — мультивибір, зберігається JSON-масивом кодів. */
    public enum Feature {
        BALCONY, LOGGIA, TERRACE, FURNISHED, APPLIANCES, AIR_CONDITIONING, STORAGE_ROOM,
        UNDERGROUND_PARKING, GARAGE, SECURITY, CONCIERGE, VIDEO_SURVEILLANCE, CLOSED_AREA, PLAYGROUND,
        BACKUP_POWER, SHELTER,
        POOL, SAUNA, GARDEN, FENCE, SUMMER_KITCHEN,
        GAS, ELECTRICITY, WATER_CENTRAL, WATER_WELL, SEWAGE_CENTRAL, SEPTIC,
        SEPARATE_ENTRANCE, SHOP_WINDOW
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    @Column(length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    private Market market;

    /** Квартира/офіс № у межах будівлі — nullable для будинку/ділянки. */
    @Column(name = "unit_number")
    private String unitNumber;

    /** Загальна площа будівлі/приміщення; для ділянки — null. Обов'язковість — PropertyRules. */
    @Column(name = "area_sqm")
    private BigDecimal areaSqm;

    @Column(name = "living_area_sqm")
    private BigDecimal livingAreaSqm;

    @Column(name = "kitchen_area_sqm")
    private BigDecimal kitchenAreaSqm;

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

    @Column(name = "ceiling_height_m")
    private BigDecimal ceilingHeightM;

    @Enumerated(EnumType.STRING)
    @Column(name = "wall_material")
    private WallMaterial wallMaterial;

    @Enumerated(EnumType.STRING)
    private Condition condition;

    @Enumerated(EnumType.STRING)
    private Heating heating;

    @Enumerated(EnumType.STRING)
    @Column(name = "land_purpose")
    private LandPurpose landPurpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "commercial_type")
    private CommercialType commercialType;

    /** Кадастровий номер (UA: XXXXXXXXXX:XX:XXX:XXXX) — для ділянки/будинку. */
    @Column(name = "cadastral_number")
    private String cadastralNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Feature> features = new ArrayList<>();

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

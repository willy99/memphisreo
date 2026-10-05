package com.memphisreo.search;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Один денормалізований рядок на кожен Listing, у СПІЛЬНІЙ (не
 * tenant-scoped) схемі `search` — розв'язує колізію schema-per-tenant
 * (ізоляція) vs публічний крос-tenant пошук (потребує спільного індексу).
 * Пишеться явним викликом з ListingService, не тригером БД.
 * docs/architecture.md §8.
 */
@Entity
@Table(name = "listing_search_document", schema = "search")
@Getter
@Setter
@NoArgsConstructor
public class ListingSearchDocument {

    /** = listing_id, 1:1 з Listing — не окремий сурогатний ключ. */
    @Id
    private UUID listingId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "deal_type", nullable = false)
    private String dealType;

    @Column(name = "property_type", nullable = false)
    private String propertyType;

    @Column(nullable = false)
    private String status;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    private String region;
    private String city;
    private String district;

    @Column(name = "geo_location", columnDefinition = "geography(Point,4326)")
    private Point geoLocation;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "area_sqm")
    private BigDecimal areaSqm;

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

    @Column(name = "description")
    private String description;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}

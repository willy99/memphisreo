package com.memphisreo.property;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/**
 * Одна будівля — багато {@link Property} (новобудова: десятки юнітів на
 * одній адресі й точці мапи) — docs/domain-model.md §3.
 */
@Entity
@Table(name = "address")
@Getter
@Setter
@NoArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    private String region;

    @Column(nullable = false)
    private String city;

    private String district;

    @Column(nullable = false)
    private String street;

    @Column(name = "house_number", nullable = false)
    private String houseNumber;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "geo_location", columnDefinition = "geography(Point,4326)")
    private Point geoLocation;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

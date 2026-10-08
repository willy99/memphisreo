package com.memphisreo.property;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import org.hibernate.annotations.TenantId;

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

    private String city;

    private String district;

    private String street;

    @Column(name = "house_number")
    private String houseNumber;

    /** Житловий комплекс (ЖК) — за ним шукають покупці новобудов. */
    @Column(name = "complex_name")
    private String complexName;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "geo_location", columnDefinition = "geography(Point,4326)")
    private Point geoLocation;

    public enum GeocodeSource { AUTOCOMPLETE, PIN, MANUAL }

    @Enumerated(EnumType.STRING)
    @Column(name = "geocode_source")
    private GeocodeSource geocodeSource;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

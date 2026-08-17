package com.memphisreo.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Агенція. Control plane — docs/domain-model.md §1. */
@Entity
@Table(name = "tenant", schema = "control_plane")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    public enum Status { TRIAL, ACTIVE, SUSPENDED, CHURNED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    /** EU / UA — яка регіональна інфраструктура обслуговує tenant-схему. */
    @Column(nullable = false)
    private String region;

    @Column(name = "schema_name", nullable = false, unique = true)
    private String schemaName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.TRIAL;

    @Column(name = "subscription_plan")
    private String subscriptionPlan;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

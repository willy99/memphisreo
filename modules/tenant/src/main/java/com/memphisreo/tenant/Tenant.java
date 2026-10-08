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

    /**
     * Призначається при реєстрації ДО відкриття транзакції: під цим tenant-ом
     * відкривається сесія, у якій створюються його ролі й перший агент (ADR-001).
     */
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    /** EU / UA — регіон, у якому живуть дані tenant-а. */
    @Column(nullable = false)
    private String region;

    /** Комірка (PostgreSQL-кластер) з даними tenant-а — ADR-001. */
    @Column(nullable = false)
    private String cell;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.TRIAL;

    @Column(name = "subscription_plan")
    private String subscriptionPlan;

    /**
     * Deal створюється автоматично при Lead.status→WON, чи явною дією
     * агента — docs/domain-model.md §6. За замовчуванням false (безпечніший
     * дефолт — не створювати записи без явної дії).
     */
    @Column(name = "auto_create_deal_on_won", nullable = false)
    private boolean autoCreateDealOnWon = false;

    // Публічні контакти для сторінки агенції — docs/sales-workflow.md §2.
    @Column(name = "public_phone")
    private String publicPhone;

    @Column(name = "public_email")
    private String publicEmail;

    private String website;

    @Column(columnDefinition = "text")
    private String about;

    @Column(name = "public_city")
    private String publicCity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

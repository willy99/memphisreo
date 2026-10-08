package com.memphisreo.platform.platformadmin;

import com.memphisreo.property.Property;
import com.memphisreo.tenant.Tenant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTO платформної адмінки — не віддаємо сутності control plane назовні як є. */
public final class PlatformAdminDtos {

    private PlatformAdminDtos() {
    }

    public record TenantSummary(UUID id, String name, String slug, String countryCode, String region,
                                Tenant.Status status, Instant createdAt, long agents, long properties) {
    }

    public record TenantPage(List<TenantSummary> items, int page, int size, long total) {
    }

    public record PropertyRow(UUID id, Property.Type type, Property.Status status, BigDecimal areaSqm,
                              Integer rooms, String city, String district, String street,
                              String houseNumber, String unitNumber, Instant createdAt) {
    }
}

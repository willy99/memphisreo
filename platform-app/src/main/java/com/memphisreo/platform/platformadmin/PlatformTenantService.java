package com.memphisreo.platform.platformadmin;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.TenantContext;
import com.memphisreo.platform.dashboard.TenantStats;
import com.memphisreo.platform.dashboard.TenantStatsService;
import com.memphisreo.platform.platformadmin.PlatformAdminDtos.PropertyRow;
import com.memphisreo.platform.platformadmin.PlatformAdminDtos.TenantPage;
import com.memphisreo.platform.platformadmin.PlatformAdminDtos.TenantSummary;
import com.memphisreo.property.Address;
import com.memphisreo.property.AddressRepository;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyRepository;
import com.memphisreo.tenant.Tenant;
import com.memphisreo.tenant.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Перегляд агенцій платформним адміном. Реєстр агенцій — control plane
 * (без RLS). Дані всередині агенції читаються НЕ в обхід RLS, а явно від
 * імені конкретної агенції ({@link TenantContext#callAs}) — той самий шлях
 * і ті самі рубежі ізоляції, що й для її агентів (ADR-001). Окрема роль БД
 * з BYPASSRLS для цього не потрібна.
 */
@Service
public class PlatformTenantService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TenantRepository tenantRepository;
    private final TenantStatsService tenantStatsService;
    private final PropertyRepository propertyRepository;
    private final AddressRepository addressRepository;

    public PlatformTenantService(TenantRepository tenantRepository,
                                 TenantStatsService tenantStatsService,
                                 PropertyRepository propertyRepository,
                                 AddressRepository addressRepository) {
        this.tenantRepository = tenantRepository;
        this.tenantStatsService = tenantStatsService;
        this.propertyRepository = propertyRepository;
        this.addressRepository = addressRepository;
    }

    /** Статистика рахується по рядку сторінки — сторінка обмежена, тож це N малих запитів, не N по всіх агенціях. */
    public TenantPage list(int page, int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Tenant> tenants = tenantRepository.findAll(request);
        List<TenantSummary> items = tenants.getContent().stream().map(this::summarize).toList();
        return new TenantPage(items, tenants.getNumber(), tenants.getSize(), tenants.getTotalElements());
    }

    public TenantSummary get(UUID tenantId) {
        return summarize(findTenant(tenantId));
    }

    public List<PropertyRow> properties(UUID tenantId) {
        Tenant tenant = findTenant(tenantId);
        return TenantContext.callAs(tenant.getId(), () -> {
            List<Property> properties = propertyRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
            Map<UUID, Address> addresses = addressRepository
                    .findAllById(properties.stream().map(Property::getAddressId).distinct().toList())
                    .stream().collect(Collectors.toMap(Address::getId, Function.identity()));
            return properties.stream().map(p -> toRow(p, addresses.get(p.getAddressId()))).toList();
        });
    }

    private TenantSummary summarize(Tenant tenant) {
        TenantStats stats = TenantContext.callAs(tenant.getId(), tenantStatsService::currentTenantStats);
        return new TenantSummary(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getCountryCode(),
                tenant.getRegion(), tenant.getStatus(), tenant.getCreatedAt(), stats.agents(), stats.properties());
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Агенцію не знайдено: " + tenantId));
    }

    private static PropertyRow toRow(Property p, Address a) {
        return new PropertyRow(p.getId(), p.getType(), p.getStatus(), p.getAreaSqm(), p.getRooms(),
                a != null ? a.getCity() : null, a != null ? a.getDistrict() : null,
                a != null ? a.getStreet() : null, a != null ? a.getHouseNumber() : null,
                p.getUnitNumber(), p.getCreatedAt());
    }
}

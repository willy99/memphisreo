package com.memphisreo.common.multitenancy;

import com.memphisreo.common.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tenant поточної Hibernate-сесії для дискримінаторної мультитенантності
 * ({@code @TenantId}) — рубіж 1 з ADR-001. Без {@link TenantContext}
 * повертає {@link #NO_TENANT}: фільтр Hibernate не знайде жодного рядка,
 * а RLS отримає той самий id — fail closed на обох рубежах.
 */
@Component
public class CurrentTenantResolver implements CurrentTenantIdentifierResolver<UUID> {

    public static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.current().orElse(NO_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}

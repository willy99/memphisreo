package com.memphisreo.common.multitenancy;

import com.memphisreo.common.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Схема поточного запиту для Hibernate SCHEMA multi-tenancy. Читає
 * {@link TenantContext}, заповнений JWT-фільтром (docs/security.md §7).
 * Немає TenantContext (platform-admin запит) → CONTROL_PLANE_SCHEMA.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    public static final String CONTROL_PLANE_SCHEMA = "control_plane";

    @Override
    public String resolveCurrentTenantIdentifier() {
        try {
            return TenantContext.get().schemaName();
        } catch (IllegalStateException e) {
            return CONTROL_PLANE_SCHEMA;
        }
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}

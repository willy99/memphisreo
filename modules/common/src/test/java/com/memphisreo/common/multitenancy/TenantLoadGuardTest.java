package com.memphisreo.common.multitenancy;

import org.hibernate.annotations.TenantId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantLoadGuardTest {

    private final TenantLoadGuard guard = new TenantLoadGuard();

    static class TenantScoped {
        @TenantId
        UUID tenantId;
    }

    static class ControlPlane {
        UUID id;
    }

    @Test
    void entityOfSessionTenant_passes() {
        UUID tenant = UUID.randomUUID();
        TenantScoped entity = new TenantScoped();
        entity.tenantId = tenant;

        assertThatCode(() -> guard.check(entity, tenant)).doesNotThrowAnyException();
    }

    @Test
    void entityOfAnotherTenant_isRejected() {
        TenantScoped entity = new TenantScoped();
        entity.tenantId = UUID.randomUUID();

        assertThatThrownBy(() -> guard.check(entity, UUID.randomUUID()))
                .isInstanceOf(TenantIsolationViolationException.class);
    }

    @Test
    void entityWithoutTenantId_isIgnored() {
        assertThatCode(() -> guard.check(new ControlPlane(), UUID.randomUUID()))
                .doesNotThrowAnyException();
    }
}

package com.memphisreo.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void callAs_setsTenantForActionAndClearsAfter() {
        UUID tenant = UUID.randomUUID();

        UUID seen = TenantContext.callAs(tenant, () -> TenantContext.current().orElseThrow());

        assertThat(seen).isEqualTo(tenant);
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void callAs_restoresPreviousTenant_evenWhenActionThrows() {
        UUID outer = UUID.randomUUID();
        TenantContext.set(outer);

        assertThatThrownBy(() -> TenantContext.runAs(UUID.randomUUID(), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(TenantContext.current()).contains(outer);
    }
}

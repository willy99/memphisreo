package com.memphisreo.platform.config;

import com.memphisreo.common.multitenancy.CurrentTenantResolver;
import com.memphisreo.common.multitenancy.RlsTenantConnectionProvider;
import com.memphisreo.common.multitenancy.TenantLoadGuard;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Shared schema + RLS — docs/adr/001-shared-schema-rls-cells.md.
 * Рубіж 1: Hibernate {@code @TenantId} з tenant-ом з {@link CurrentTenantResolver}
 * + {@link TenantLoadGuard} для завантажень за ключем.
 * Рубіж 2: {@link RlsTenantConnectionProvider} виставляє app.tenant_id для RLS.
 */
@Configuration
public class MultiTenancyConfig {

    @Bean
    public HibernatePropertiesCustomizer multiTenancyCustomizer(CurrentTenantResolver currentTenantResolver,
                                                                 RlsTenantConnectionProvider connectionProvider) {
        return properties -> {
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, currentTenantResolver);
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put("hibernate.integrator_provider",
                    (IntegratorProvider) () -> List.of(new TenantLoadGuard()));
        };
    }
}

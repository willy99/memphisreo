package com.memphisreo.platform.config;

import com.memphisreo.common.multitenancy.SchemaMultiTenantConnectionProvider;
import com.memphisreo.common.multitenancy.TenantIdentifierResolver;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Реєструє Hibernate SCHEMA multi-tenancy — docs/architecture.md §3. */
@Configuration
public class MultiTenancyConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(
            SchemaMultiTenantConnectionProvider connectionProvider,
            TenantIdentifierResolver tenantIdentifierResolver) {
        return properties -> {
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        };
    }
}

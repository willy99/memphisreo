package com.memphisreo.tenant;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.regex.Pattern;

/**
 * Онбординг нового tenant: створює схему і накочує tenant-baseline
 * міграції. Викликається один раз при реєстрації агенції, не при старті
 * застосунку (яке мігрує лише control-plane) — docs/architecture.md §3.
 */
@Service
public class TenantProvisioningService {

    // Схема генерується нашим кодом (напр. "tenant_" + UUID), ніколи не
    // приймається напряму від клієнта — перевірка тут другий рубіж, не перший.
    private static final Pattern SAFE_SCHEMA_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public TenantProvisioningService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void provision(String schemaName) {
        if (!SAFE_SCHEMA_NAME.matcher(schemaName).matches()) {
            throw new IllegalArgumentException("Некоректна назва схеми: " + schemaName);
        }
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");

        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations("classpath:db/migration/tenant-baseline")
                .load()
                .migrate();
    }
}

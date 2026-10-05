package com.memphisreo.platform.maintenance;

import com.memphisreo.common.TenantContext;
import com.memphisreo.security.rbac.DefaultRoleSeeder;
import com.memphisreo.tenant.Tenant;
import com.memphisreo.tenant.TenantProvisioningService;
import com.memphisreo.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Доганяє tenant-и, зареєстровані до появи нових Flyway-міграцій чи нових
 * permission-кодів — docs/architecture.md §3, docs/domain-model.md §5.
 *
 * Синхронний прогін на старті — прийнятно для кількості tenant-ів, яку
 * очікуємо в Фазі 1-2. При зростанні кількості tenant-ів це стане вузьким
 * місцем старту застосунку — тоді переносити у фоновий job.
 */
@Component
public class TenantMaintenanceRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantMaintenanceRunner.class);

    private final TenantRepository tenantRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final DefaultRoleSeeder defaultRoleSeeder;

    public TenantMaintenanceRunner(TenantRepository tenantRepository,
                                    TenantProvisioningService tenantProvisioningService,
                                    DefaultRoleSeeder defaultRoleSeeder) {
        this.tenantRepository = tenantRepository;
        this.tenantProvisioningService = tenantProvisioningService;
        this.defaultRoleSeeder = defaultRoleSeeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Tenant tenant : tenantRepository.findAll()) {
            try {
                // Flyway.migrate() ідемпотентний — на вже актуальній схемі це no-op.
                tenantProvisioningService.provision(tenant.getSchemaName());

                try {
                    TenantContext.set(new TenantContext.TenantInfo(tenant.getId().toString(), tenant.getSchemaName()));
                    defaultRoleSeeder.syncSystemDefaults(tenant.getId());
                } finally {
                    TenantContext.clear();
                }
            } catch (Exception e) {
                // Один зламаний tenant не має блокувати старт застосунку для решти.
                log.error("Не вдалося довести tenant {} до актуального стану", tenant.getId(), e);
            }
        }
    }
}

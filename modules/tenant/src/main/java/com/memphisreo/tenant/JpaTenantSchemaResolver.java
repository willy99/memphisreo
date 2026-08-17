package com.memphisreo.tenant;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.multitenancy.TenantSchemaResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JpaTenantSchemaResolver implements TenantSchemaResolver {

    private final TenantRepository tenantRepository;

    public JpaTenantSchemaResolver(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public String resolveSchema(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(Tenant::getSchemaName)
                .orElseThrow(() -> new NotFoundException("Tenant не знайдено: " + tenantId));
    }
}

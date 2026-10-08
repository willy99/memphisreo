package com.memphisreo.platform.dashboard;

import com.memphisreo.agent.AgentRepository;
import com.memphisreo.property.PropertyRepository;
import org.springframework.stereotype.Service;

/**
 * Статистика агенції ПОТОЧНОГО TenantContext — рахує лише її рядки
 * (@TenantId + RLS). Платформний адмін викликає це під
 * {@code TenantContext.callAs(tenantId, ...)} конкретної агенції.
 */
@Service
public class TenantStatsService {

    private final AgentRepository agentRepository;
    private final PropertyRepository propertyRepository;

    public TenantStatsService(AgentRepository agentRepository, PropertyRepository propertyRepository) {
        this.agentRepository = agentRepository;
        this.propertyRepository = propertyRepository;
    }

    public TenantStats currentTenantStats() {
        return new TenantStats(agentRepository.count(), propertyRepository.count());
    }
}

package com.memphisreo.platform.registration;

import java.util.UUID;

public record RegisterTenantResponse(UUID tenantId, String schemaName, UUID adminAgentId) {
}

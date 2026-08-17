package com.memphisreo.security.jwt;

import java.util.UUID;

/** Principal, встановлений {@link TenantJwtAuthenticationFilter} з claim'ів JWT. */
public record AuthenticatedAgent(UUID accountIdentityId, UUID agentId, UUID tenantId) {
}

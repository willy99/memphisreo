package com.memphisreo.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountIdentityRepository extends JpaRepository<AccountIdentity, UUID> {

    Optional<AccountIdentity> findByEmail(String email);

    Optional<AccountIdentity> findByInviteToken(String inviteToken);

    Optional<AccountIdentity> findByAgentId(UUID agentId);

    List<AccountIdentity> findByTenantId(UUID tenantId);
}

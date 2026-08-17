package com.memphisreo.security.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentRoleRepository extends JpaRepository<AgentRole, UUID> {

    List<AgentRole> findByAgentId(UUID agentId);
}

package com.memphisreo.security.rbac;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Розгортає AgentRole → Role → RolePermission у плаский набір кодів для JWT claim. */
@Service
public class AgentPermissionResolver {

    private final AgentRoleRepository agentRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public AgentPermissionResolver(AgentRoleRepository agentRoleRepository,
                                    RolePermissionRepository rolePermissionRepository) {
        this.agentRoleRepository = agentRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public Set<String> resolve(UUID agentId) {
        List<UUID> roleIds = agentRoleRepository.findByAgentId(agentId).stream()
                .map(AgentRole::getRoleId)
                .toList();

        if (roleIds.isEmpty()) {
            return Set.of();
        }

        return rolePermissionRepository.findByRoleIdIn(roleIds).stream()
                .map(RolePermission::getPermissionCode)
                .collect(java.util.stream.Collectors.toSet());
    }
}

package com.memphisreo.security.rbac;

import com.memphisreo.common.ForbiddenException;
import com.memphisreo.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD над кастомними ролями — "конфігурується онлайн" з docs/security.md §3.
 * {@link DefaultRoleSeeder} керує лише двома built-in роля­ми, цей сервіс —
 * будь-якою роллю, включно з built-in (permissions можна редагувати, сам
 * запис — ні, якщо is_system_default).
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AgentRoleRepository agentRoleRepository;

    public RoleService(RoleRepository roleRepository, RolePermissionRepository rolePermissionRepository,
                        AgentRoleRepository agentRoleRepository) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.agentRoleRepository = agentRoleRepository;
    }

    /** Повністю замінює набір ролей агента (не додає — саме замінює). */
    public void assignRoles(UUID agentId, Set<UUID> roleIds) {
        agentRoleRepository.deleteAll(agentRoleRepository.findByAgentId(agentId));
        for (UUID roleId : roleIds) {
            AgentRole assignment = new AgentRole();
            assignment.setAgentId(agentId);
            assignment.setRoleId(roleId);
            agentRoleRepository.save(assignment);
        }
    }

    public List<Role> list(UUID tenantId) {
        return roleRepository.findByTenantId(tenantId);
    }

    public Set<String> permissionsOf(UUID roleId) {
        return rolePermissionRepository.findByRoleId(roleId).stream()
                .map(RolePermission::getPermissionCode)
                .collect(Collectors.toSet());
    }

    public Role create(UUID tenantId, String name, Set<Permission> permissions) {
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setName(name);
        role.setSystemDefault(false);
        role = roleRepository.save(role);
        replacePermissions(role.getId(), permissions);
        return role;
    }

    /** Повністю замінює набір прав ролі — і для кастомних, і для built-in. */
    public Role updatePermissions(UUID roleId, Set<Permission> permissions) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Роль не знайдено: " + roleId));
        replacePermissions(role.getId(), permissions);
        return role;
    }

    public void delete(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Роль не знайдено: " + roleId));
        if (role.isSystemDefault()) {
            throw new ForbiddenException("Вбудовану роль не можна видалити: " + role.getName());
        }
        rolePermissionRepository.deleteAll(rolePermissionRepository.findByRoleId(roleId));
        roleRepository.delete(role);
    }

    private void replacePermissions(UUID roleId, Set<Permission> permissions) {
        rolePermissionRepository.deleteAll(rolePermissionRepository.findByRoleId(roleId));
        for (Permission permission : permissions) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionCode(permission.name());
            rolePermissionRepository.save(rolePermission);
        }
    }
}

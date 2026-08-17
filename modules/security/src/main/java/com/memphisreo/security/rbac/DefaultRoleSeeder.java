package com.memphisreo.security.rbac;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Дві built-in ролі на кожен новий tenant — docs/security.md §3. Викликається
 * TenantRegistrationService одразу після провіжинування схеми, з TenantContext,
 * уже виставленим на щойно створену схему.
 */
@Service
public class DefaultRoleSeeder {

    private static final List<Permission> AGENT_DEFAULT_PERMISSIONS = List.of(
            Permission.PROPERTY_VIEW,
            Permission.PROPERTY_CREATE,
            Permission.PROPERTY_EDIT,
            Permission.LISTING_VIEW,
            Permission.LISTING_PUBLISH,
            Permission.LISTING_EDIT,
            Permission.LISTING_CLOSE,
            Permission.INQUIRY_VIEW,
            Permission.INQUIRY_MANAGE
    );

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public DefaultRoleSeeder(RoleRepository roleRepository, RolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    /** @return роль TENANT_ADMIN (усі permissions), для призначення першому агенту. */
    public Role seed(UUID tenantId) {
        Role tenantAdmin = createRole(tenantId, "Tenant Admin");
        grantAll(tenantAdmin, List.of(Permission.values()));

        Role agent = createRole(tenantId, "Agent");
        grantAll(agent, AGENT_DEFAULT_PERMISSIONS);

        return tenantAdmin;
    }

    private Role createRole(UUID tenantId, String name) {
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setName(name);
        role.setSystemDefault(true);
        return roleRepository.save(role);
    }

    private void grantAll(Role role, List<Permission> permissions) {
        for (Permission permission : permissions) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(role.getId());
            rolePermission.setPermissionCode(permission.name());
            rolePermissionRepository.save(rolePermission);
        }
    }
}

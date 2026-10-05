package com.memphisreo.security.rbac;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Дві built-in ролі на кожен новий tenant — docs/security.md §3. {@link #seed}
 * викликається TenantRegistrationService у транзакції реєстрації, під
 * TenantContext щойно створеного tenant-а. Новий permission-код для вже
 * існуючих tenant-ів додається міграцією даних, тільки додаючи (ADR-001).
 */
@Service
public class DefaultRoleSeeder {

    private static final String TENANT_ADMIN_ROLE_NAME = "Tenant Admin";
    private static final String AGENT_ROLE_NAME = "Agent";

    private static final List<Permission> AGENT_DEFAULT_PERMISSIONS = List.of(
            Permission.PROPERTY_VIEW,
            Permission.PROPERTY_CREATE,
            Permission.PROPERTY_EDIT,
            Permission.LISTING_VIEW,
            Permission.LISTING_PUBLISH,
            Permission.LISTING_EDIT,
            Permission.LISTING_CLOSE,
            Permission.INQUIRY_VIEW,
            Permission.INQUIRY_MANAGE,
            Permission.LEAD_VIEW,
            Permission.LEAD_MANAGE,
            Permission.CLIENT_VIEW,
            Permission.CLIENT_MANAGE,
            Permission.DOCUMENT_VIEW,
            Permission.DOCUMENT_MANAGE
            // DOCUMENT_VIEW_CONFIDENTIAL свідомо відсутній у дефолтному наборі —
            // лістер+юрист отримують його через окрему кастомну роль, не за замовчуванням.
    );

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public DefaultRoleSeeder(RoleRepository roleRepository, RolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    /** @return роль TENANT_ADMIN (усі permissions), для призначення першому агенту. */
    public Role seed(UUID tenantId) {
        Role tenantAdmin = createRole(tenantId, TENANT_ADMIN_ROLE_NAME);
        grantMissing(tenantAdmin, List.of(Permission.values()));

        Role agent = createRole(tenantId, AGENT_ROLE_NAME);
        grantMissing(agent, AGENT_DEFAULT_PERMISSIONS);

        return tenantAdmin;
    }

    private Role createRole(UUID tenantId, String name) {
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setName(name);
        role.setSystemDefault(true);
        return roleRepository.save(role);
    }

    private void grantMissing(Role role, List<Permission> permissions) {
        Set<String> alreadyGranted = rolePermissionRepository.findByRoleId(role.getId()).stream()
                .map(RolePermission::getPermissionCode)
                .collect(Collectors.toSet());

        for (Permission permission : permissions) {
            if (alreadyGranted.contains(permission.name())) {
                continue;
            }
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(role.getId());
            rolePermission.setPermissionCode(permission.name());
            rolePermissionRepository.save(rolePermission);
        }
    }
}

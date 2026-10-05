package com.memphisreo.platform.api;

import com.memphisreo.security.jwt.AuthenticatedAgent;
import com.memphisreo.security.rbac.Permission;
import com.memphisreo.security.rbac.Role;
import com.memphisreo.security.rbac.RoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class RoleController {

    public record RoleResponse(UUID id, String name, boolean isSystemDefault, Set<String> permissions) {
    }

    public record CreateRoleRequest(String name, Set<Permission> permissions) {
    }

    public record UpdatePermissionsRequest(Set<Permission> permissions) {
    }

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).ROLE_MANAGE.name())")
    public ResponseEntity<List<Permission>> permissionCatalog() {
        return ResponseEntity.ok(List.of(Permission.values()));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).ROLE_MANAGE.name())")
    public ResponseEntity<List<RoleResponse>> list(@AuthenticationPrincipal AuthenticatedAgent principal) {
        List<RoleResponse> roles = roleService.list(principal.tenantId()).stream()
                .map(role -> new RoleResponse(role.getId(), role.getName(), role.isSystemDefault(),
                        roleService.permissionsOf(role.getId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(roles);
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).ROLE_MANAGE.name())")
    public ResponseEntity<Role> create(@AuthenticationPrincipal AuthenticatedAgent principal,
                                        @RequestBody CreateRoleRequest request) {
        return ResponseEntity.ok(roleService.create(principal.tenantId(), request.name(), request.permissions()));
    }

    @PatchMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).ROLE_MANAGE.name())")
    public ResponseEntity<Role> updatePermissions(@PathVariable UUID id, @RequestBody UpdatePermissionsRequest request) {
        return ResponseEntity.ok(roleService.updatePermissions(id, request.permissions()));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).ROLE_MANAGE.name())")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

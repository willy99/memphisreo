package com.memphisreo.security.rbac;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Зв'язок роль → permission_code. permission_code — рядкове значення
 * {@link Permission#name()}, не FK на окрему таблицю (docs/security.md §3).
 */
@Entity
@Table(name = "role_permission")
@Getter
@Setter
@NoArgsConstructor
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_code", nullable = false)
    private String permissionCode;
}

package com.memphisreo.security.rbac;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Зв'язок агент → роль (багато-до-багатьох). agent_id — сире посилання на
 * Agent з модуля {@code agent}, не JPA-зв'язок через межу модуля.
 */
@Entity
@Table(name = "agent_role")
@Getter
@Setter
@NoArgsConstructor
public class AgentRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;
}

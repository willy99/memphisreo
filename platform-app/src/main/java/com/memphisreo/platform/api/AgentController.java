package com.memphisreo.platform.api;

import com.memphisreo.agent.Agent;
import com.memphisreo.agent.AgentRepository;
import com.memphisreo.common.NotFoundException;
import com.memphisreo.platform.agent.InviteAgentRequest;
import com.memphisreo.platform.agent.InviteAgentResponse;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import com.memphisreo.platform.agent.AgentAccountService;
import com.memphisreo.platform.agent.AgentInvitationService;
import com.memphisreo.security.rbac.RoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    public record UpdateAgentStatusRequest(Agent.Status status) {
    }

    public record AssignRolesRequest(Set<UUID> roleIds) {
    }

    private final AgentRepository agentRepository;
    private final AgentInvitationService agentInvitationService;
    private final RoleService roleService;
    private final AgentAccountService agentAccountService;

    public AgentController(AgentRepository agentRepository, AgentInvitationService agentInvitationService,
                            RoleService roleService, AgentAccountService agentAccountService) {
        this.agentRepository = agentRepository;
        this.agentInvitationService = agentInvitationService;
        this.roleService = roleService;
        this.agentAccountService = agentAccountService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).AGENT_MANAGE.name())")
    public ResponseEntity<List<Agent>> list() {
        return ResponseEntity.ok(agentRepository.findAll());
    }

    /** Власний профіль — без окремого permission, будь-який автентифікований агент бачить себе. */
    @GetMapping("/me")
    public ResponseEntity<Agent> me(@AuthenticationPrincipal AuthenticatedAgent principal) {
        Agent agent = agentRepository.findById(principal.agentId())
                .orElseThrow(() -> new NotFoundException("Agent не знайдено: " + principal.agentId()));
        return ResponseEntity.ok(agent);
    }

    @PostMapping("/invite")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).AGENT_INVITE.name())")
    public ResponseEntity<InviteAgentResponse> invite(@AuthenticationPrincipal AuthenticatedAgent principal,
                                                        @RequestBody InviteAgentRequest request) {
        return ResponseEntity.ok(agentInvitationService.invite(principal.tenantId(), request));
    }

    /** Деактивація блокує й логін (AgentAccountService), не лише профіль. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).AGENT_MANAGE.name())")
    public ResponseEntity<Agent> updateStatus(@PathVariable UUID id, @RequestBody UpdateAgentStatusRequest request) {
        agentAccountService.setActive(id, request.status() == Agent.Status.ACTIVE);
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Agent не знайдено: " + id));
        return ResponseEntity.ok(agent);
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).AGENT_MANAGE.name())")
    public ResponseEntity<Void> assignRoles(@PathVariable UUID id, @RequestBody AssignRolesRequest request) {
        roleService.assignRoles(id, request.roleIds());
        return ResponseEntity.ok().build();
    }
}

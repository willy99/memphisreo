package com.memphisreo.platform;

import com.memphisreo.agent.Agent;
import com.memphisreo.platform.agent.AcceptInviteRequest;
import com.memphisreo.platform.agent.InviteAgentRequest;
import com.memphisreo.platform.agent.InviteAgentResponse;
import com.memphisreo.platform.api.AgentController;
import com.memphisreo.platform.api.RoleController;
import com.memphisreo.security.rbac.Permission;
import com.memphisreo.security.rbac.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Запрошення агента + керування ролями — Фаза "Адмінка агенції", docs/security.md §9. */
class AgentInviteAndRoleFlowIT extends AbstractIntegrationTest {

    @Test
    void inviteAgent_acceptInvite_thenLoginWithDefaultAgentPermissions() {
        String slug = "invite-" + UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin@" + slug + ".ua";
        register(slug, adminEmail, "AdminPass123!", "UA");
        String adminToken = login(adminEmail, "AdminPass123!");

        String agentEmail = "agent@" + slug + ".ua";
        InviteAgentRequest inviteRequest = new InviteAgentRequest(agentEmail, "Олег", "Іваненко", null);
        ResponseEntity<InviteAgentResponse> inviteResponse = restTemplate.exchange(
                "/api/agents/invite", HttpMethod.POST, authed(adminToken, inviteRequest), InviteAgentResponse.class);
        assertThat(inviteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = inviteResponse.getBody().inviteToken();
        assertThat(token).isNotBlank();

        AcceptInviteRequest acceptRequest = new AcceptInviteRequest(token, "AgentPass123!");
        ResponseEntity<Void> acceptResponse = restTemplate.postForEntity(
                "/api/auth/accept-invite", acceptRequest, Void.class);
        assertThat(acceptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String agentToken = login(agentEmail, "AgentPass123!");
        assertThat(agentToken).isNotBlank();

        // AGENT — може створювати property (стандартний набір), але не запрошувати інших агентів.
        createProperty(agentToken);

        ResponseEntity<String> forbiddenInvite = restTemplate.exchange(
                "/api/agents/invite", HttpMethod.POST,
                authed(agentToken, new InviteAgentRequest("someone@" + slug + ".ua", "X", "Y", null)),
                String.class);
        assertThat(forbiddenInvite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Agent[]> agentsList = restTemplate.exchange(
                "/api/agents", HttpMethod.GET, authed(adminToken), Agent[].class);
        assertThat(agentsList.getBody()).extracting(Agent::getEmail).contains(agentEmail);
    }

    @Test
    void customRole_canBeCreatedAndAssigned_butSystemDefaultRoleCannotBeDeleted() {
        String slug = "role-" + UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin@" + slug + ".ua";
        register(slug, adminEmail, "AdminPass123!", "UA");
        String adminToken = login(adminEmail, "AdminPass123!");

        RoleController.CreateRoleRequest createRequest = new RoleController.CreateRoleRequest(
                "Viewer", Set.of(Permission.PROPERTY_VIEW, Permission.LISTING_VIEW));
        ResponseEntity<Role> createResponse = restTemplate.exchange(
                "/api/roles", HttpMethod.POST, authed(adminToken, createRequest), Role.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Role customRole = createResponse.getBody();

        ResponseEntity<RoleController.RoleResponse[]> rolesList = restTemplate.exchange(
                "/api/roles", HttpMethod.GET, authed(adminToken), RoleController.RoleResponse[].class);
        assertThat(List.of(rolesList.getBody()))
                .filteredOn(r -> r.id().equals(customRole.getId()))
                .singleElement()
                .satisfies(r -> assertThat(r.permissions()).containsExactlyInAnyOrder("PROPERTY_VIEW", "LISTING_VIEW"));

        Role tenantAdminRole = List.of(rolesList.getBody()).stream()
                .filter(r -> r.isSystemDefault() && "Tenant Admin".equals(r.name()))
                .findFirst().map(r -> {
                    Role role = new Role();
                    role.setId(r.id());
                    return role;
                }).orElseThrow();

        ResponseEntity<String> deleteSystemRole = restTemplate.exchange(
                "/api/roles/" + tenantAdminRole.getId(), HttpMethod.DELETE, authed(adminToken), String.class);
        assertThat(deleteSystemRole.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Void> deleteCustomRole = restTemplate.exchange(
                "/api/roles/" + customRole.getId(), HttpMethod.DELETE, authed(adminToken), Void.class);
        assertThat(deleteCustomRole.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}

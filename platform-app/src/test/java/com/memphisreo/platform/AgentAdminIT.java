package com.memphisreo.platform;

import com.memphisreo.agent.Agent;
import com.memphisreo.platform.agent.AcceptInviteRequest;
import com.memphisreo.platform.agent.AgentAccountService.AgentAccount;
import com.memphisreo.platform.agent.AgentAccountService.ResetPassword;
import com.memphisreo.platform.agent.InviteAgentRequest;
import com.memphisreo.platform.agent.InviteAgentResponse;
import com.memphisreo.platform.api.AuthController;
import com.memphisreo.platform.api.PlatformAdminController.AgentStatusRequest;
import com.memphisreo.platform.registration.RegisterTenantResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Платформний адмін керує агентами агенції: список з логінами, деактивація блокує вхід, новий пароль, видалення. */
class AgentAdminIT extends AbstractIntegrationTest {

    @Test
    void adminSeesAgents_deactivationBlocksLogin_resetPasswordWorks_deleteOnlyWithoutData() {
        String slug = "agadm-" + UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin@" + slug + ".ua";
        RegisterTenantResponse agency = register(slug, adminEmail, "Password123!", "UA");
        String agencyToken = login(adminEmail, "Password123!");
        createProperty(agencyToken); // адмін агенції тепер має об'єкт → не видаляється

        InviteAgentResponse invite = restTemplate.exchange("/api/agents/invite", HttpMethod.POST,
                authed(agencyToken, new InviteAgentRequest("agent@" + slug + ".ua", "Олена", "Шевчук", null)),
                InviteAgentResponse.class).getBody();
        restTemplate.postForEntity("/api/auth/accept-invite", new AcceptInviteRequest(invite.inviteToken(), "Password123!"), Void.class);

        String platform = platformLogin();
        String base = "/platform-admin/tenants/" + agency.tenantId() + "/agents";
        List<AgentAccount> agents = List.of(restTemplate.exchange(base, HttpMethod.GET, authed(platform), AgentAccount[].class).getBody());
        assertThat(agents).extracting(AgentAccount::email).containsExactly(adminEmail, "agent@" + slug + ".ua");
        assertThat(agents.get(0).deletable()).isFalse();
        assertThat(agents.get(1).deletable()).isTrue();

        // Деактивація: профіль DISABLED, логін блокується, виданий токен відкликано.
        String agentToken = login("agent@" + slug + ".ua", "Password123!");
        AgentAccount disabled = restTemplate.exchange(base + "/" + invite.agentId() + "/status", HttpMethod.PATCH,
                authed(platform, new AgentStatusRequest(false)), AgentAccount.class).getBody();
        assertThat(disabled.status()).isEqualTo(Agent.Status.DISABLED);
        assertThat(restTemplate.postForEntity("/api/auth/login", new AuthController.LoginRequest("agent@" + slug + ".ua", "Password123!"),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange("/api/agents/me", HttpMethod.GET, authed(agentToken), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Активація + новий пароль: старий не працює, новий — працює.
        restTemplate.exchange(base + "/" + invite.agentId() + "/status", HttpMethod.PATCH, authed(platform, new AgentStatusRequest(true)), AgentAccount.class);
        ResetPassword reset = restTemplate.exchange(base + "/" + invite.agentId() + "/reset-password", HttpMethod.POST,
                authed(platform), ResetPassword.class).getBody();
        assertThat(reset.password()).hasSize(14);
        assertThat(restTemplate.postForEntity("/api/auth/login", new AuthController.LoginRequest(reset.email(), "Password123!"),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(login(reset.email(), reset.password())).isNotBlank();

        // Видалення: агента без даних — можна; адміна з об'єктом — ні.
        ResponseEntity<String> refused = restTemplate.exchange(base + "/" + agency.adminAgentId(), HttpMethod.DELETE, authed(platform), String.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("hasData");
        assertThat(restTemplate.exchange(base + "/" + invite.agentId(), HttpMethod.DELETE, authed(platform), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.exchange(base, HttpMethod.GET, authed(platform), AgentAccount[].class).getBody()).hasSize(1);
        assertThat(restTemplate.postForEntity("/api/auth/login", new AuthController.LoginRequest(reset.email(), reset.password()),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}

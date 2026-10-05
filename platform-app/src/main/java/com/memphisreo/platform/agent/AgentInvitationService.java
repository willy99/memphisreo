package com.memphisreo.platform.agent;

import com.memphisreo.agent.Agent;
import com.memphisreo.agent.AgentRepository;
import com.memphisreo.common.ForbiddenException;
import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.TenantContext;
import com.memphisreo.security.AccountIdentity;
import com.memphisreo.security.AccountIdentityRepository;
import com.memphisreo.security.rbac.Role;
import com.memphisreo.security.rbac.RoleRepository;
import com.memphisreo.security.rbac.RoleService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Запрошення агента торкається agent + security модулів разом —
 * composition root, той самий принцип, що і TenantRegistrationService
 * (docs/architecture.md §2). Деталі флоу — docs/security.md §9.
 */
@Service
public class AgentInvitationService {

    private static final Duration INVITE_TTL = Duration.ofDays(7);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AgentRepository agentRepository;
    private final AccountIdentityRepository accountIdentityRepository;
    private final RoleRepository roleRepository;
    private final RoleService roleService;
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;

    public AgentInvitationService(AgentRepository agentRepository,
                                   AccountIdentityRepository accountIdentityRepository,
                                   RoleRepository roleRepository,
                                   RoleService roleService,
                                   PasswordEncoder passwordEncoder,
                                   TransactionTemplate transactionTemplate) {
        this.agentRepository = agentRepository;
        this.accountIdentityRepository = accountIdentityRepository;
        this.roleRepository = roleRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
    }

    public InviteAgentResponse invite(UUID tenantId, InviteAgentRequest request) {
        if (accountIdentityRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("Email вже зареєстровано: " + request.email());
        }

        Agent agent = new Agent();
        agent.setTenantId(tenantId);
        agent.setFirstName(request.firstName());
        agent.setLastName(request.lastName());
        agent.setEmail(request.email());
        agent.setStatus(Agent.Status.INVITED);
        agent = agentRepository.save(agent);

        UUID roleId = request.roleId() != null ? request.roleId() : defaultAgentRoleId(tenantId);
        roleService.assignRoles(agent.getId(), Set.of(roleId));

        String token = generateToken();
        Instant expiresAt = Instant.now().plus(INVITE_TTL);

        AccountIdentity account = new AccountIdentity();
        account.setEmail(request.email());
        account.setTenantId(tenantId);
        account.setAgentId(agent.getId());
        account.setStatus(AccountIdentity.Status.PENDING_INVITE);
        account.setInviteToken(token);
        account.setInviteExpiresAt(expiresAt);
        accountIdentityRepository.save(account);

        return new InviteAgentResponse(agent.getId(), request.email(), token, expiresAt);
    }

    public void acceptInvite(String token, String password) {
        AccountIdentity found = accountIdentityRepository.findByInviteToken(token)
                .orElseThrow(() -> new NotFoundException("Запрошення не знайдено"));

        if (found.getInviteExpiresAt() == null || found.getInviteExpiresAt().isBefore(Instant.now())) {
            throw new ForbiddenException("Запрошення протерміноване");
        }

        // Публічний ендпоїнт без JWT: tenant — з облікового запису (control plane),
        // знайденого за токеном. Логін і профіль агента активуються атомарно.
        TenantContext.runAs(found.getTenantId(), () -> transactionTemplate.executeWithoutResult(status -> {
            AccountIdentity account = accountIdentityRepository.findById(found.getId())
                    .orElseThrow(() -> new NotFoundException("Запрошення не знайдено"));
            account.setPasswordHash(passwordEncoder.encode(password));
            account.setStatus(AccountIdentity.Status.ACTIVE);
            account.setInviteToken(null);
            account.setInviteExpiresAt(null);
            accountIdentityRepository.save(account);

            Agent agent = agentRepository.findById(account.getAgentId())
                    .orElseThrow(() -> new NotFoundException("Agent не знайдено: " + account.getAgentId()));
            agent.setStatus(Agent.Status.ACTIVE);
            agentRepository.save(agent);
        }));
    }

    private UUID defaultAgentRoleId(UUID tenantId) {
        List<Role> roles = roleRepository.findByTenantId(tenantId);
        return roles.stream()
                .filter(r -> r.isSystemDefault() && "Agent".equals(r.getName()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Дефолтну роль Agent не знайдено для tenant " + tenantId))
                .getId();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

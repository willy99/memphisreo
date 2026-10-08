package com.memphisreo.platform.agent;

import com.memphisreo.agent.Agent;
import com.memphisreo.agent.AgentRepository;
import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.ValidationException;
import com.memphisreo.common.ValidationException.FieldError;
import com.memphisreo.listing.ListingRepository;
import com.memphisreo.property.PropertyAgentRepository;
import com.memphisreo.property.PropertyRepository;
import com.memphisreo.security.AccountIdentity;
import com.memphisreo.security.AccountIdentityRepository;
import com.memphisreo.security.rbac.AgentRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Профіль агента (tenant plane) + його логін (control plane) змінюються разом:
 * деактивація блокує вхід і відкликає видані токени, активація повертає.
 * Використовується і адмінкою агенції, і платформним адміном.
 */
@Service
public class AgentAccountService {

    public record AgentAccount(UUID id, String firstName, String lastName, String email, String phone,
                               Agent.Status status, AccountIdentity.Status loginStatus, Instant createdAt,
                               boolean deletable) {
    }

    public record ResetPassword(String email, String password) {
    }

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AgentRepository agentRepository;
    private final AccountIdentityRepository accountRepository;
    private final AgentRoleRepository agentRoleRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyAgentRepository propertyAgentRepository;
    private final ListingRepository listingRepository;
    private final PasswordEncoder passwordEncoder;

    public AgentAccountService(AgentRepository agentRepository, AccountIdentityRepository accountRepository,
                               AgentRoleRepository agentRoleRepository, PropertyRepository propertyRepository,
                               PropertyAgentRepository propertyAgentRepository, ListingRepository listingRepository,
                               PasswordEncoder passwordEncoder) {
        this.agentRepository = agentRepository;
        this.accountRepository = accountRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.propertyRepository = propertyRepository;
        this.propertyAgentRepository = propertyAgentRepository;
        this.listingRepository = listingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Агенти поточного tenant-а з логінами. */
    public List<AgentAccount> list(UUID tenantId) {
        Map<UUID, AccountIdentity> accounts = accountRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(AccountIdentity::getAgentId, Function.identity(), (a, b) -> a));
        return agentRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Agent::getCreatedAt))
                .map(agent -> toView(agent, accounts.get(agent.getId())))
                .toList();
    }

    @Transactional
    public AgentAccount setActive(UUID agentId, boolean active) {
        Agent agent = find(agentId);
        agent.setStatus(active ? Agent.Status.ACTIVE : Agent.Status.DISABLED);
        AccountIdentity account = accountRepository.findByAgentId(agentId).orElse(null);
        if (account != null) {
            if (active) {
                account.setStatus(account.getPasswordHash() == null ? AccountIdentity.Status.PENDING_INVITE
                        : AccountIdentity.Status.ACTIVE);
            } else {
                account.setStatus(AccountIdentity.Status.DISABLED);
                account.setTokenVersion(account.getTokenVersion() + 1); // миттєво завершує сесії
            }
        }
        return toView(agent, account);
    }

    /**
     * Новий пароль для агента (адмін передає його особисто). Старі сесії
     * завершуються. Повертається один раз і більше ніде не показується.
     */
    @Transactional
    public ResetPassword resetPassword(UUID agentId) {
        Agent agent = find(agentId);
        AccountIdentity account = accountRepository.findByAgentId(agentId)
                .orElseThrow(() -> new NotFoundException("У агента немає логіну"));
        String password = generatePassword();
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setTokenVersion(account.getTokenVersion() + 1);
        account.setInviteToken(null);
        account.setInviteExpiresAt(null);
        if (account.getStatus() == AccountIdentity.Status.PENDING_INVITE) {
            account.setStatus(AccountIdentity.Status.ACTIVE);
            agent.setStatus(Agent.Status.ACTIVE);
        }
        return new ResetPassword(account.getEmail(), password);
    }

    /** Видалити можна лише агента без слідів у даних — інакше деактивувати. */
    @Transactional
    public void delete(UUID agentId) {
        Agent agent = find(agentId);
        if (!isDeletable(agentId)) {
            throw new ValidationException("Агент має об'єкти або угоди — його можна лише деактивувати",
                    List.of(new FieldError("agent", "hasData")));
        }
        agentRoleRepository.deleteAll(agentRoleRepository.findByAgentId(agentId));
        accountRepository.findByAgentId(agentId).ifPresent(accountRepository::delete);
        agentRepository.delete(agent);
    }

    private boolean isDeletable(UUID agentId) {
        return propertyAgentRepository.findByAgentId(agentId).isEmpty()
                && !propertyRepository.existsByCreatedByAgentId(agentId)
                && !listingRepository.existsByAgentId(agentId);
    }

    private Agent find(UUID agentId) {
        return agentRepository.findById(agentId).orElseThrow(() -> new NotFoundException("Agent не знайдено: " + agentId));
    }

    private AgentAccount toView(Agent agent, AccountIdentity account) {
        return new AgentAccount(agent.getId(), agent.getFirstName(), agent.getLastName(),
                account != null ? account.getEmail() : agent.getEmail(), agent.getPhone(), agent.getStatus(),
                account != null ? account.getStatus() : null, agent.getCreatedAt(), isDeletable(agent.getId()));
    }

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}

package com.memphisreo.platform.registration;

import com.memphisreo.agent.Agent;
import com.memphisreo.agent.AgentRepository;
import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.TenantContext;
import com.memphisreo.security.AccountIdentity;
import com.memphisreo.security.AccountIdentityRepository;
import com.memphisreo.security.rbac.AgentRole;
import com.memphisreo.security.rbac.AgentRoleRepository;
import com.memphisreo.security.rbac.DefaultRoleSeeder;
import com.memphisreo.security.rbac.Role;
import com.memphisreo.tenant.Country;
import com.memphisreo.tenant.CountryRepository;
import com.memphisreo.tenant.Tenant;
import com.memphisreo.tenant.TenantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Єдине місце в системі, що орхеструє tenant+agent+security модулі разом —
 * навмисно тут, у composition root, не в жодному з доменних модулів, щоб не
 * давати їм залежності одне на одного. docs/architecture.md §2.
 *
 * Провіжинінг = рядок у control plane + вбудовані ролі + перший агент і його
 * логін, усе в ОДНІЙ транзакції (ADR-001: без створення схем). id tenant-а
 * генерується заздалегідь, щоб транзакція відкрилась уже під ним — інакше
 * Hibernate @TenantId і RLS не дадуть вставити рядки нового tenant-а.
 */
@Service
public class TenantRegistrationService {

    private final TenantRepository tenantRepository;
    private final CountryRepository countryRepository;
    private final AgentRepository agentRepository;
    private final AccountIdentityRepository accountIdentityRepository;
    private final AgentRoleRepository agentRoleRepository;
    private final DefaultRoleSeeder defaultRoleSeeder;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final String cell;

    public TenantRegistrationService(TenantRepository tenantRepository,
                                      CountryRepository countryRepository,
                                      AgentRepository agentRepository,
                                      AccountIdentityRepository accountIdentityRepository,
                                      AgentRoleRepository agentRoleRepository,
                                      DefaultRoleSeeder defaultRoleSeeder,
                                      PasswordEncoder passwordEncoder,
                                      TransactionTemplate transactionTemplate,
                                      @Value("${memphisreo.cell}") String cell) {
        this.tenantRepository = tenantRepository;
        this.countryRepository = countryRepository;
        this.agentRepository = agentRepository;
        this.accountIdentityRepository = accountIdentityRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.defaultRoleSeeder = defaultRoleSeeder;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.cell = cell;
    }

    public RegisterTenantResponse register(RegisterTenantRequest request) {
        UUID tenantId = UUID.randomUUID();
        return TenantContext.callAs(tenantId,
                () -> transactionTemplate.execute(status -> registerInTransaction(tenantId, request)));
    }

    private RegisterTenantResponse registerInTransaction(UUID tenantId, RegisterTenantRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new IllegalArgumentException("Slug вже зайнятий: " + request.slug());
        }
        if (accountIdentityRepository.findByEmail(request.adminEmail()).isPresent()) {
            throw new IllegalArgumentException("Email вже зареєстровано: " + request.adminEmail());
        }
        Country country = countryRepository.findById(request.countryCode())
                .orElseThrow(() -> new NotFoundException("Невідома країна: " + request.countryCode()));

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName(request.agencyName());
        tenant.setSlug(request.slug());
        tenant.setCountryCode(country.getCode());
        tenant.setRegion(country.getRegion());
        tenant.setCell(cell);
        tenantRepository.save(tenant);

        Role tenantAdminRole = defaultRoleSeeder.seed(tenantId);

        Agent adminAgent = new Agent();
        adminAgent.setFirstName(request.adminFirstName());
        adminAgent.setLastName(request.adminLastName());
        adminAgent.setEmail(request.adminEmail());
        adminAgent.setStatus(Agent.Status.ACTIVE);
        adminAgent.setTenantId(tenantId);
        adminAgent = agentRepository.save(adminAgent);

        AgentRole assignment = new AgentRole();
        assignment.setAgentId(adminAgent.getId());
        assignment.setRoleId(tenantAdminRole.getId());
        agentRoleRepository.save(assignment);

        AccountIdentity account = new AccountIdentity();
        account.setEmail(request.adminEmail());
        account.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
        account.setTenantId(tenantId);
        account.setAgentId(adminAgent.getId());
        account.setStatus(AccountIdentity.Status.ACTIVE);
        accountIdentityRepository.save(account);

        return new RegisterTenantResponse(tenantId, adminAgent.getId());
    }
}

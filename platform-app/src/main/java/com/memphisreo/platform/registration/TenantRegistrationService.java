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
import com.memphisreo.tenant.TenantProvisioningService;
import com.memphisreo.tenant.TenantRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Єдине місце в системі, що орхеструє tenant+agent+security модулі разом —
 * навмисно тут, у composition root, не в жодному з доменних модулів, щоб не
 * давати їм залежності одне на одного. docs/architecture.md §2.
 *
 * ВІДОМЕ ОБМЕЖЕННЯ цього скелета: немає компенсуючої транзакції — якщо крок
 * після provision() впаде (напр. збереження Agent), tenant-схема лишиться
 * створеною, а control-plane запис Tenant — ні (create-only demo-код).
 * Requires a saga/outbox або ручний retry-механізм перед проду.
 */
@Service
public class TenantRegistrationService {

    private final TenantRepository tenantRepository;
    private final CountryRepository countryRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final AgentRepository agentRepository;
    private final AccountIdentityRepository accountIdentityRepository;
    private final AgentRoleRepository agentRoleRepository;
    private final DefaultRoleSeeder defaultRoleSeeder;
    private final PasswordEncoder passwordEncoder;

    public TenantRegistrationService(TenantRepository tenantRepository,
                                      CountryRepository countryRepository,
                                      TenantProvisioningService tenantProvisioningService,
                                      AgentRepository agentRepository,
                                      AccountIdentityRepository accountIdentityRepository,
                                      AgentRoleRepository agentRoleRepository,
                                      DefaultRoleSeeder defaultRoleSeeder,
                                      PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.countryRepository = countryRepository;
        this.tenantProvisioningService = tenantProvisioningService;
        this.agentRepository = agentRepository;
        this.accountIdentityRepository = accountIdentityRepository;
        this.agentRoleRepository = agentRoleRepository;
        this.defaultRoleSeeder = defaultRoleSeeder;
        this.passwordEncoder = passwordEncoder;
    }

    public RegisterTenantResponse register(RegisterTenantRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new IllegalArgumentException("Slug вже зайнятий: " + request.slug());
        }
        Country country = countryRepository.findById(request.countryCode())
                .orElseThrow(() -> new NotFoundException("Невідома країна: " + request.countryCode()));

        String schemaName = toSchemaName(request.slug());

        Tenant tenant = new Tenant();
        tenant.setName(request.agencyName());
        tenant.setSlug(request.slug());
        tenant.setCountryCode(country.getCode());
        tenant.setRegion(country.getRegion());
        tenant.setSchemaName(schemaName);
        tenant = tenantRepository.save(tenant);

        // DDL — поза Hibernate-транзакцією, власне з'єднання Flyway.
        tenantProvisioningService.provision(schemaName);

        Agent adminAgent;
        Role tenantAdminRole;
        try {
            TenantContext.set(new TenantContext.TenantInfo(tenant.getId().toString(), schemaName));

            tenantAdminRole = defaultRoleSeeder.seed(tenant.getId());

            adminAgent = new Agent();
            adminAgent.setFirstName(request.adminFirstName());
            adminAgent.setLastName(request.adminLastName());
            adminAgent.setEmail(request.adminEmail());
            adminAgent.setStatus(Agent.Status.ACTIVE);
            adminAgent.setTenantId(tenant.getId());
            adminAgent = agentRepository.save(adminAgent);

            AgentRole assignment = new AgentRole();
            assignment.setAgentId(adminAgent.getId());
            assignment.setRoleId(tenantAdminRole.getId());
            agentRoleRepository.save(assignment);
        } finally {
            TenantContext.clear();
        }

        AccountIdentity account = new AccountIdentity();
        account.setEmail(request.adminEmail());
        account.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
        account.setTenantId(tenant.getId());
        account.setAgentId(adminAgent.getId());
        account.setStatus(AccountIdentity.Status.ACTIVE);
        accountIdentityRepository.save(account);

        return new RegisterTenantResponse(tenant.getId(), schemaName, adminAgent.getId());
    }

    private String toSchemaName(String slug) {
        String normalized = slug.toLowerCase().replaceAll("[^a-z0-9]", "_");
        String candidate = "tenant_" + normalized;
        return candidate.length() > 63 ? candidate.substring(0, 63) : candidate;
    }
}

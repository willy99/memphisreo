package com.memphisreo.security;

import com.memphisreo.common.ForbiddenException;
import com.memphisreo.common.TenantContext;
import com.memphisreo.common.multitenancy.TenantSchemaResolver;
import com.memphisreo.security.jwt.JwtService;
import com.memphisreo.security.rbac.AgentPermissionResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Логін tenant-агента. Permissions читаються з tenant-схеми — TenantContext
 * тут виставляється ВРУЧНУ (немає ще JWT на цьому кроці), не через
 * JWT-фільтр. Легітимний ручний TenantContext поза request-scoped
 * фільтром — тут і в будь-якому іншому public-ендпоїнті, що резолвить
 * tenant за slug з path (напр. PublicInquiryController), а не з JWT.
 * docs/security.md §4.
 */
@Service
public class LoginService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final AccountIdentityRepository accountIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService tenantJwtService;
    private final TenantSchemaResolver tenantSchemaResolver;
    private final AgentPermissionResolver agentPermissionResolver;

    public LoginService(AccountIdentityRepository accountIdentityRepository,
                         PasswordEncoder passwordEncoder,
                         @Qualifier("tenantJwtService") JwtService tenantJwtService,
                         TenantSchemaResolver tenantSchemaResolver,
                         AgentPermissionResolver agentPermissionResolver) {
        this.accountIdentityRepository = accountIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantJwtService = tenantJwtService;
        this.tenantSchemaResolver = tenantSchemaResolver;
        this.agentPermissionResolver = agentPermissionResolver;
    }

    public record LoginResult(String accessToken, Duration expiresIn) {
    }

    public LoginResult login(String email, String rawPassword) {
        AccountIdentity account = accountIdentityRepository.findByEmail(email)
                .orElseThrow(() -> new ForbiddenException("Невірний email або пароль"));

        if (account.getStatus() != AccountIdentity.Status.ACTIVE
                || !passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            throw new ForbiddenException("Невірний email або пароль");
        }

        if (account.isTwoFactorEnabled()) {
            // Демо Фази 1 не реалізує TOTP-верифікацію — свідома відмова,
            // не мовчазний пропуск 2FA. docs/security.md §5.
            throw new ForbiddenException("2FA увімкнено для цього акаунту — verify-крок ще не реалізований");
        }

        String schemaName = tenantSchemaResolver.resolveSchema(account.getTenantId());
        Set<String> permissions;
        try {
            TenantContext.set(new TenantContext.TenantInfo(account.getTenantId().toString(), schemaName));
            permissions = agentPermissionResolver.resolve(account.getAgentId());
        } finally {
            TenantContext.clear();
        }

        Map<String, Object> claims = Map.of(
                "tenant_id", account.getTenantId().toString(),
                "agent_id", account.getAgentId().toString(),
                "schema_name", schemaName,
                "token_version", account.getTokenVersion(),
                "permissions", List.copyOf(permissions)
        );

        String token = tenantJwtService.issueAccessToken(claims, account.getId().toString(), ACCESS_TOKEN_TTL);
        return new LoginResult(token, ACCESS_TOKEN_TTL);
    }
}

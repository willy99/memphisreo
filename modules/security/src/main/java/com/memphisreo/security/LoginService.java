package com.memphisreo.security;

import com.memphisreo.common.ForbiddenException;
import com.memphisreo.common.TenantContext;
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
 * Логін tenant-агента. Permissions читаються з tenant plane під RLS — тож
 * TenantContext тут виставляється ВРУЧНУ з tenant_id облікового запису
 * (control plane), бо JWT на цьому кроці ще немає. Той самий легітимний
 * виняток, що й у PublicInquiryController (tenant за slug). docs/security.md §4.
 */
@Service
public class LoginService {

    /**
     * Тимчасово довгий TTL: refresh-токенів ще немає (docs/security.md §4),
     * а миттєве відкликання вже працює через token_version.
     */
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(8);

    private final AccountIdentityRepository accountIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService tenantJwtService;
    private final AgentPermissionResolver agentPermissionResolver;

    public LoginService(AccountIdentityRepository accountIdentityRepository,
                         PasswordEncoder passwordEncoder,
                         @Qualifier("tenantJwtService") JwtService tenantJwtService,
                         AgentPermissionResolver agentPermissionResolver) {
        this.accountIdentityRepository = accountIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantJwtService = tenantJwtService;
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

        Set<String> permissions = TenantContext.callAs(account.getTenantId(),
                () -> agentPermissionResolver.resolve(account.getAgentId()));

        Map<String, Object> claims = Map.of(
                "tenant_id", account.getTenantId().toString(),
                "agent_id", account.getAgentId().toString(),
                "token_version", account.getTokenVersion(),
                "permissions", List.copyOf(permissions)
        );

        String token = tenantJwtService.issueAccessToken(claims, account.getId().toString(), ACCESS_TOKEN_TTL);
        return new LoginResult(token, ACCESS_TOKEN_TTL);
    }
}

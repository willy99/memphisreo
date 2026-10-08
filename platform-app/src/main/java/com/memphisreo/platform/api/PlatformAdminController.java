package com.memphisreo.platform.api;

import com.memphisreo.platform.platformadmin.PlatformAdminDtos.PropertyRow;
import com.memphisreo.platform.platformadmin.PlatformAdminDtos.TenantPage;
import com.memphisreo.platform.platformadmin.PlatformAdminDtos.TenantSummary;
import com.memphisreo.platform.platformadmin.PlatformTenantService;
import com.memphisreo.platform.agent.AgentAccountService;
import com.memphisreo.platform.agent.AgentAccountService.AgentAccount;
import com.memphisreo.platform.agent.AgentAccountService.ResetPassword;
import com.memphisreo.common.TenantContext;
import com.memphisreo.platform.registration.RegisterTenantRequest;
import com.memphisreo.platform.registration.RegisterTenantResponse;
import com.memphisreo.platform.registration.TenantRegistrationService;
import com.memphisreo.security.LoginService;
import com.memphisreo.security.PlatformLoginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Платформна адмінка: окремий SecurityFilterChain і ключ JWT (docs/security.md §6).
 * Кожен вхід у дані агенції логується — хто з платформи що дивився.
 */
@RestController
@RequestMapping("/platform-admin")
public class PlatformAdminController {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminController.class);
    private static final String IS_PLATFORM_ADMIN = "hasAuthority('" + PlatformLoginService.PLATFORM_ADMIN + "')";

    public record LoginRequest(String email, String password) {
    }

    private final PlatformLoginService platformLoginService;
    private final PlatformTenantService platformTenantService;
    private final TenantRegistrationService tenantRegistrationService;
    private final AgentAccountService agentAccountService;

    public PlatformAdminController(PlatformLoginService platformLoginService,
                                   PlatformTenantService platformTenantService,
                                   TenantRegistrationService tenantRegistrationService,
                                   AgentAccountService agentAccountService) {
        this.platformLoginService = platformLoginService;
        this.platformTenantService = platformTenantService;
        this.tenantRegistrationService = tenantRegistrationService;
        this.agentAccountService = agentAccountService;
    }

    public record AgentStatusRequest(boolean active) {
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginService.LoginResult> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(platformLoginService.login(request.email(), request.password()));
    }

    @GetMapping("/tenants")
    @PreAuthorize(IS_PLATFORM_ADMIN)
    public ResponseEntity<TenantPage> tenants(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(platformTenantService.list(page, size));
    }

    @PostMapping("/tenants")
    @PreAuthorize(IS_PLATFORM_ADMIN)
    public ResponseEntity<RegisterTenantResponse> createTenant(@AuthenticationPrincipal UUID staffId,
                                                               @RequestBody RegisterTenantRequest request) {
        RegisterTenantResponse response = tenantRegistrationService.register(request);
        log.info("platform-admin {} створив агенцію {} ({})", staffId, response.tenantId(), request.slug());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tenants/{tenantId}")
    @PreAuthorize(IS_PLATFORM_ADMIN)
    public ResponseEntity<TenantSummary> tenant(@AuthenticationPrincipal UUID staffId, @PathVariable UUID tenantId) {
        log.info("platform-admin {} переглядає агенцію {}", staffId, tenantId);
        return ResponseEntity.ok(platformTenantService.get(tenantId));
    }

    @GetMapping("/tenants/{tenantId}/properties")
    @PreAuthorize(IS_PLATFORM_ADMIN)
    public ResponseEntity<List<PropertyRow>> tenantProperties(@AuthenticationPrincipal UUID staffId,
                                                              @PathVariable UUID tenantId) {
        log.info("platform-admin {} переглядає об'єкти агенції {}", staffId, tenantId);
        return ResponseEntity.ok(platformTenantService.properties(tenantId));
    }

    // ---------- Агенти агенції (від імені агенції, під її RLS) ----------

    @GetMapping("/tenants/{tenantId}/agents")
    @PreAuthorize(IS_PLATFORM_ADMIN)
    public ResponseEntity<List<AgentAccount>> tenantAgents(@AuthenticationPrincipal UUID staffId, @PathVariable UUID tenantId) {
        platformTenantService.get(tenantId);
        log.info("platform-admin {} переглядає агентів агенції {}", staffId, tenantId);
        return ResponseEntity.ok(TenantContext.callAs(tenantId, () -> agentAccountService.list(tenantId)));
    }

    @PatchMapping("/tenants/{tenantId}/agents/{agentId}/status")
    @PreAuthorize(IS_PLATFORM_ADMIN)
    public ResponseEntity<AgentAccount> setAgentStatus(@AuthenticationPrincipal UUID staffId, @PathVariable UUID tenantId,
                                                       @PathVariable UUID agentId, @RequestBody AgentStatusRequest request) {
        platformTenantService.get(tenantId);
        log.info("platform-admin {} {} агента {} в агенції {}", staffId, request.active() ? "активує" : "деактивує", agentId, tenantId);
        return ResponseEntity.ok(TenantContext.callAs(tenantId, () -> agentAccountService.setActive(agentId, request.active())));
    }

    @PostMapping("/tenants/{tenantId}/agents/{agentId}/reset-password")
    @PreAuthorize(IS_PLATFORM_ADMIN)
    public ResponseEntity<ResetPassword> resetAgentPassword(@AuthenticationPrincipal UUID staffId, @PathVariable UUID tenantId,
                                                            @PathVariable UUID agentId) {
        platformTenantService.get(tenantId);
        log.info("platform-admin {} скидає пароль агента {} в агенції {}", staffId, agentId, tenantId);
        return ResponseEntity.ok(TenantContext.callAs(tenantId, () -> agentAccountService.resetPassword(agentId)));
    }

    @DeleteMapping("/tenants/{tenantId}/agents/{agentId}")
    @PreAuthorize(IS_PLATFORM_ADMIN)
    public ResponseEntity<Void> deleteAgent(@AuthenticationPrincipal UUID staffId, @PathVariable UUID tenantId,
                                            @PathVariable UUID agentId) {
        platformTenantService.get(tenantId);
        log.info("platform-admin {} видаляє агента {} в агенції {}", staffId, agentId, tenantId);
        TenantContext.runAs(tenantId, () -> agentAccountService.delete(agentId));
        return ResponseEntity.noContent().build();
    }
}

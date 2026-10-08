package com.memphisreo.platform.api;

import com.memphisreo.platform.platformadmin.PlatformAdminDtos.PropertyRow;
import com.memphisreo.platform.platformadmin.PlatformAdminDtos.TenantPage;
import com.memphisreo.platform.platformadmin.PlatformAdminDtos.TenantSummary;
import com.memphisreo.platform.platformadmin.PlatformTenantService;
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

    public PlatformAdminController(PlatformLoginService platformLoginService,
                                   PlatformTenantService platformTenantService,
                                   TenantRegistrationService tenantRegistrationService) {
        this.platformLoginService = platformLoginService;
        this.platformTenantService = platformTenantService;
        this.tenantRegistrationService = tenantRegistrationService;
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
}

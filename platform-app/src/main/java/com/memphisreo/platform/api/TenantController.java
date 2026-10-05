package com.memphisreo.platform.api;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import com.memphisreo.tenant.Tenant;
import com.memphisreo.tenant.TenantRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant")
public class TenantController {

    public record UpdateTenantRequest(String name, Boolean autoCreateDealOnWon) {
    }

    private final TenantRepository tenantRepository;

    public TenantController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).TENANT_SETTINGS_MANAGE.name())")
    public ResponseEntity<Tenant> get(@AuthenticationPrincipal AuthenticatedAgent principal) {
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new NotFoundException("Tenant не знайдено: " + principal.tenantId()));
        return ResponseEntity.ok(tenant);
    }

    @PatchMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).TENANT_SETTINGS_MANAGE.name())")
    public ResponseEntity<Tenant> update(@AuthenticationPrincipal AuthenticatedAgent principal,
                                          @RequestBody UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new NotFoundException("Tenant не знайдено: " + principal.tenantId()));
        tenant.setName(request.name());
        if (request.autoCreateDealOnWon() != null) {
            tenant.setAutoCreateDealOnWon(request.autoCreateDealOnWon());
        }
        return ResponseEntity.ok(tenantRepository.save(tenant));
    }
}

package com.memphisreo.platform.api;

import com.memphisreo.platform.owner.OwnerPortalService;
import com.memphisreo.platform.owner.OwnerPortalService.OwnerReport;
import com.memphisreo.platform.sale.SaleDtos.OwnerLink;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Звіт власнику: агент видає посилання; власник відкриває його без логіну. */
@RestController
public class OwnerPortalController {

    private final OwnerPortalService ownerPortalService;

    public OwnerPortalController(OwnerPortalService ownerPortalService) {
        this.ownerPortalService = ownerPortalService;
    }

    @PostMapping("/api/clients/{clientId}/owner-link")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_MANAGE.name())")
    public ResponseEntity<OwnerLink> issue(@AuthenticationPrincipal AuthenticatedAgent principal, @PathVariable UUID clientId) {
        return ResponseEntity.ok(ownerPortalService.issueLink(principal.tenantId(), principal.agentId(), clientId));
    }

    /** Публічний (permitAll у SecurityConfig): автентифікація — сам токен. */
    @GetMapping("/api/public/owner/{token}")
    public ResponseEntity<OwnerReport> report(@PathVariable String token) {
        return ResponseEntity.ok(ownerPortalService.report(token));
    }
}

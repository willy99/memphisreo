package com.memphisreo.platform.api;

import com.memphisreo.listing.SaleForm;
import com.memphisreo.platform.sale.SaleDtos.AgentView;
import com.memphisreo.platform.sale.SaleDtos.AgentsRequest;
import com.memphisreo.platform.sale.SaleDtos.SaleView;
import com.memphisreo.platform.sale.SaleDtos.SoldRequest;
import com.memphisreo.platform.sale.SaleDtos.TimelineEntry;
import com.memphisreo.platform.sale.SaleDtos.WithdrawRequest;
import com.memphisreo.platform.sale.SaleService;
import com.memphisreo.platform.crm.MatchingService;
import com.memphisreo.platform.crm.MatchingService.ClientMatch;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Вкладка "Продаж" об'єкта + відповідальні агенти + таймлайн. Заміна старого /api/listings. */
@RestController
@RequestMapping("/api/properties/{propertyId}")
public class PropertySaleController {

    private static final String VIEW = "hasAuthority(T(com.memphisreo.security.rbac.Permission).LISTING_VIEW.name())";
    private static final String EDIT = "hasAuthority(T(com.memphisreo.security.rbac.Permission).LISTING_EDIT.name())";
    private static final String PUBLISH = "hasAuthority(T(com.memphisreo.security.rbac.Permission).LISTING_PUBLISH.name())";
    private static final String CLOSE = "hasAuthority(T(com.memphisreo.security.rbac.Permission).LISTING_CLOSE.name())";

    private final SaleService saleService;
    private final MatchingService matchingService;

    public PropertySaleController(SaleService saleService, MatchingService matchingService) {
        this.saleService = saleService;
        this.matchingService = matchingService;
    }

    /** Кому з клієнтів (за їх запитами) підходить цей об'єкт. */
    @GetMapping("/matching-clients")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_VIEW.name())")
    public ResponseEntity<List<ClientMatch>> matchingClients(@PathVariable UUID propertyId) {
        saleService.view(propertyId);
        return ResponseEntity.ok(matchingService.clientsForProperty(propertyId));
    }

    @GetMapping("/sale")
    @PreAuthorize(VIEW)
    public ResponseEntity<SaleView> get(@PathVariable UUID propertyId) {
        return ResponseEntity.ok(saleService.view(propertyId));
    }

    @PutMapping("/sale")
    @PreAuthorize(EDIT)
    public ResponseEntity<SaleView> update(@AuthenticationPrincipal AuthenticatedAgent p, @PathVariable UUID propertyId,
                                           @RequestBody SaleForm form) {
        return ResponseEntity.ok(saleService.update(p.tenantId(), p.agentId(), propertyId, form));
    }

    @PostMapping("/sale/activate")
    @PreAuthorize(PUBLISH)
    public ResponseEntity<SaleView> activate(@AuthenticationPrincipal AuthenticatedAgent p, @PathVariable UUID propertyId) {
        return ResponseEntity.ok(saleService.activate(p.tenantId(), p.agentId(), propertyId));
    }

    @PostMapping("/sale/withdraw")
    @PreAuthorize(PUBLISH)
    public ResponseEntity<SaleView> withdraw(@AuthenticationPrincipal AuthenticatedAgent p, @PathVariable UUID propertyId,
                                             @RequestBody(required = false) WithdrawRequest request) {
        return ResponseEntity.ok(saleService.withdraw(p.tenantId(), p.agentId(), propertyId,
                request == null ? null : request.reason()));
    }

    @PostMapping("/sale/sold")
    @PreAuthorize(CLOSE)
    public ResponseEntity<SaleView> sold(@AuthenticationPrincipal AuthenticatedAgent p, @PathVariable UUID propertyId,
                                         @RequestBody(required = false) SoldRequest request) {
        return ResponseEntity.ok(saleService.markSold(p.tenantId(), p.agentId(), propertyId,
                request == null ? null : request.finalPrice()));
    }

    @GetMapping("/agents")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<AgentView>> agents(@PathVariable UUID propertyId) {
        return ResponseEntity.ok(saleService.agents(propertyId));
    }

    @PutMapping("/agents")
    @PreAuthorize(EDIT)
    public ResponseEntity<List<AgentView>> setAgents(@AuthenticationPrincipal AuthenticatedAgent p,
                                                     @PathVariable UUID propertyId, @RequestBody AgentsRequest request) {
        return ResponseEntity.ok(saleService.setAgents(p.tenantId(), p.agentId(), propertyId, request));
    }

    @GetMapping("/timeline")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).PROPERTY_VIEW.name())")
    public ResponseEntity<List<TimelineEntry>> timeline(@PathVariable UUID propertyId) {
        return ResponseEntity.ok(saleService.timeline(propertyId, false));
    }
}

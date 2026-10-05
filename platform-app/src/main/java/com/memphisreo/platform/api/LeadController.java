package com.memphisreo.platform.api;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.crm.Lead;
import com.memphisreo.crm.LeadActivity;
import com.memphisreo.crm.LeadActivityRepository;
import com.memphisreo.crm.LeadRepository;
import com.memphisreo.crm.LeadService;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    public record UpdateStatusRequest(Lead.Status status) {
    }

    public record AddActivityRequest(String note) {
    }

    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;
    private final LeadService leadService;

    public LeadController(LeadRepository leadRepository, LeadActivityRepository leadActivityRepository,
                           LeadService leadService) {
        this.leadRepository = leadRepository;
        this.leadActivityRepository = leadActivityRepository;
        this.leadService = leadService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).LEAD_VIEW.name())")
    public ResponseEntity<List<Lead>> list() {
        return ResponseEntity.ok(leadRepository.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).LEAD_VIEW.name())")
    public ResponseEntity<Lead> get(@PathVariable UUID id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lead не знайдено: " + id));
        return ResponseEntity.ok(lead);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).LEAD_MANAGE.name())")
    public ResponseEntity<Lead> updateStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(leadService.updateStatus(id, request.status()));
    }

    @GetMapping("/{id}/activities")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).LEAD_VIEW.name())")
    public ResponseEntity<List<LeadActivity>> activities(@PathVariable UUID id) {
        return ResponseEntity.ok(leadActivityRepository.findByLeadIdOrderByCreatedAtDesc(id));
    }

    @PostMapping("/{id}/activities")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).LEAD_MANAGE.name())")
    public ResponseEntity<LeadActivity> addActivity(@AuthenticationPrincipal AuthenticatedAgent principal,
                                                     @PathVariable UUID id,
                                                     @RequestBody AddActivityRequest request) {
        return ResponseEntity.ok(leadService.addActivity(id, principal.agentId(), request.note()));
    }
}

package com.memphisreo.platform.api;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.crm.Client;
import com.memphisreo.crm.Lead;
import com.memphisreo.crm.LeadService;
import com.memphisreo.inquiry.Inquiry;
import com.memphisreo.inquiry.InquiryRepository;
import com.memphisreo.inquiry.InquiryService;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inquiries")
public class InquiryController {

    private final InquiryRepository inquiryRepository;
    private final InquiryService inquiryService;
    private final LeadService leadService;

    public InquiryController(InquiryRepository inquiryRepository, InquiryService inquiryService,
                              LeadService leadService) {
        this.inquiryRepository = inquiryRepository;
        this.inquiryService = inquiryService;
        this.leadService = leadService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).INQUIRY_VIEW.name())")
    public ResponseEntity<List<Inquiry>> list() {
        return ResponseEntity.ok(inquiryRepository.findAll());
    }

    /** Client dedup за email, Lead прив'язується до агента й лістингу з inquiry. */
    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).LEAD_MANAGE.name())")
    public ResponseEntity<Lead> convert(@AuthenticationPrincipal AuthenticatedAgent principal,
                                         @PathVariable UUID id) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Inquiry не знайдено: " + id));

        String[] nameParts = inquiry.getContactName().trim().split("\\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : "";

        Client client = leadService.findOrCreateClient(principal.tenantId(), firstName, lastName,
                inquiry.getContactEmail(), inquiry.getContactPhone(), Client.Source.WEBSITE_INQUIRY);

        Lead lead = leadService.createLead(principal.tenantId(), client.getId(), inquiry.getListingId(),
                inquiry.getAgentId(), inquiry.getId());

        inquiryService.markConverted(inquiry.getId(), lead.getId());

        return ResponseEntity.ok(lead);
    }
}

package com.memphisreo.platform.api;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.TenantContext;
import com.memphisreo.inquiry.CreateInquiryRequest;
import com.memphisreo.inquiry.Inquiry;
import com.memphisreo.inquiry.InquiryService;
import com.memphisreo.listing.Listing;
import com.memphisreo.listing.ListingRepository;
import com.memphisreo.tenant.Tenant;
import com.memphisreo.tenant.TenantRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Публічна форма "зв'язатись зі мною" — немає JWT, тому tenant резолвиться
 * за slug з path, не з claim'у. TenantContext виставляється вручну на час
 * запиту — легітимний виняток поза JWT-фільтром, docs/security.md §4.
 */
@RestController
@RequestMapping("/api/public/tenants/{tenantSlug}/listings/{listingId}/inquiries")
public class PublicInquiryController {

    private final TenantRepository tenantRepository;
    private final ListingRepository listingRepository;
    private final InquiryService inquiryService;

    public PublicInquiryController(TenantRepository tenantRepository,
                                    ListingRepository listingRepository,
                                    InquiryService inquiryService) {
        this.tenantRepository = tenantRepository;
        this.listingRepository = listingRepository;
        this.inquiryService = inquiryService;
    }

    @PostMapping
    public ResponseEntity<Inquiry> submit(@PathVariable String tenantSlug,
                                           @PathVariable UUID listingId,
                                           @RequestBody CreateInquiryRequest request) {
        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
                .orElseThrow(() -> new NotFoundException("Агенцію не знайдено: " + tenantSlug));

        try {
            TenantContext.set(new TenantContext.TenantInfo(tenant.getId().toString(), tenant.getSchemaName()));

            Listing listing = listingRepository.findById(listingId)
                    .orElseThrow(() -> new NotFoundException("Лістинг не знайдено: " + listingId));

            Inquiry inquiry = inquiryService.create(tenant.getId(), listingId, listing.getAgentId(),
                    request.contactName(), request.contactEmail(), request.contactPhone(), request.message());

            return ResponseEntity.ok(inquiry);
        } finally {
            TenantContext.clear();
        }
    }
}

package com.memphisreo.inquiry;

import com.memphisreo.common.ForbiddenException;
import com.memphisreo.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * agent_id тут — уже резолвлений виклику (з Listing.agentId), не
 * приймається напряму від публічного відправника форми — резолюція
 * відбувається в platform-app (потребує модуля listing). docs/architecture.md §2.
 */
@Service
public class InquiryService {

    private final InquiryRepository inquiryRepository;

    public InquiryService(InquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    public Inquiry create(UUID tenantId, UUID listingId, UUID agentId, String contactName,
                           String contactEmail, String contactPhone, String message) {
        Inquiry inquiry = new Inquiry();
        inquiry.setTenantId(tenantId);
        inquiry.setListingId(listingId);
        inquiry.setAgentId(agentId);
        inquiry.setContactName(contactName);
        inquiry.setContactEmail(contactEmail);
        inquiry.setContactPhone(contactPhone);
        inquiry.setMessage(message);
        inquiry.setStatus(Inquiry.Status.NEW);
        return inquiryRepository.save(inquiry);
    }

    public Inquiry markConverted(UUID inquiryId, UUID leadId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new NotFoundException("Inquiry не знайдено: " + inquiryId));
        if (inquiry.getConvertedToLeadId() != null) {
            throw new ForbiddenException("Inquiry вже сконвертовано в lead: " + inquiry.getConvertedToLeadId());
        }
        inquiry.setConvertedToLeadId(leadId);
        inquiry.setStatus(Inquiry.Status.CONTACTED);
        return inquiryRepository.save(inquiry);
    }
}

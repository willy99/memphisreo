package com.memphisreo.crm;

import com.memphisreo.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Самодостатній сервіс модуля crm. Конвертація Inquiry → Lead (яка
 * зачіпає й модуль inquiry) орхеструється в platform-app, не тут —
 * docs/architecture.md §2.
 */
@Service
public class LeadService {

    private final ClientRepository clientRepository;
    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;

    public LeadService(ClientRepository clientRepository,
                        LeadRepository leadRepository,
                        LeadActivityRepository leadActivityRepository) {
        this.clientRepository = clientRepository;
        this.leadRepository = leadRepository;
        this.leadActivityRepository = leadActivityRepository;
    }

    /** Dedup за email у межах поточної tenant-схеми — docs/domain-model.md §5. */
    public Client findOrCreateClient(UUID tenantId, String firstName, String lastName, String email,
                                      String phone, Client.Source source) {
        return clientRepository.findByEmail(email).orElseGet(() -> {
            Client client = new Client();
            client.setTenantId(tenantId);
            client.setFirstName(firstName);
            client.setLastName(lastName);
            client.setEmail(email);
            client.setPhone(phone);
            client.setSource(source);
            return clientRepository.save(client);
        });
    }

    public Lead createLead(UUID tenantId, UUID clientId, UUID listingId, UUID assignedAgentId,
                            UUID sourceInquiryId) {
        Lead lead = new Lead();
        lead.setTenantId(tenantId);
        lead.setClientId(clientId);
        lead.setListingId(listingId);
        lead.setAssignedAgentId(assignedAgentId);
        lead.setSourceInquiryId(sourceInquiryId);
        lead.setStatus(Lead.Status.NEW);
        return leadRepository.save(lead);
    }

    public Lead updateStatus(UUID leadId, Lead.Status status) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new NotFoundException("Lead не знайдено: " + leadId));
        lead.setStatus(status);
        lead.setUpdatedAt(Instant.now());
        return leadRepository.save(lead);
    }

    public LeadActivity addActivity(UUID leadId, UUID agentId, String note) {
        if (!leadRepository.existsById(leadId)) {
            throw new NotFoundException("Lead не знайдено: " + leadId);
        }
        LeadActivity activity = new LeadActivity();
        activity.setLeadId(leadId);
        activity.setAgentId(agentId);
        activity.setNote(note);
        return leadActivityRepository.save(activity);
    }
}

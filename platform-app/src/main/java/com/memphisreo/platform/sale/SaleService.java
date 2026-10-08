package com.memphisreo.platform.sale;

import com.memphisreo.activity.ActivityEvent;
import com.memphisreo.activity.ActivityEvent.SubjectType;
import com.memphisreo.activity.ActivityRecorder;
import com.memphisreo.agent.Agent;
import com.memphisreo.agent.AgentRepository;
import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.ValidationException;
import com.memphisreo.common.ValidationException.FieldError;
import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientRepository;
import com.memphisreo.listing.Listing;
import com.memphisreo.listing.ListingService;
import com.memphisreo.listing.ListingService.Change;
import com.memphisreo.listing.SaleForm;
import com.memphisreo.platform.sale.SaleDtos.AgentView;
import com.memphisreo.platform.sale.SaleDtos.AgentsRequest;
import com.memphisreo.platform.sale.SaleDtos.PricePoint;
import com.memphisreo.platform.sale.SaleDtos.SaleView;
import com.memphisreo.platform.sale.SaleDtos.SellerView;
import com.memphisreo.platform.sale.SaleDtos.TimelineEntry;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyAgent;
import com.memphisreo.property.PropertyAgentRepository;
import com.memphisreo.property.PropertyService;
import com.memphisreo.tenant.Tenant;
import com.memphisreo.tenant.TenantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Вкладка "Продаж": поєднує listing (ціна/мандат/статус), crm (власник),
 * property (відповідальні агенти) і activity (таймлайн). Кожна дія пише
 * подію — з них складається історія об'єкта і звіт власнику.
 */
@Service
public class SaleService {

    private final ListingService listingService;
    private final PropertyService propertyService;
    private final PropertyAgentRepository propertyAgentRepository;
    private final AgentRepository agentRepository;
    private final ClientRepository clientRepository;
    private final TenantRepository tenantRepository;
    private final ActivityRecorder activity;
    private final String webBaseUrl;

    public SaleService(ListingService listingService, PropertyService propertyService,
                       PropertyAgentRepository propertyAgentRepository, AgentRepository agentRepository,
                       ClientRepository clientRepository, TenantRepository tenantRepository,
                       ActivityRecorder activity, @Value("${memphisreo.web-base-url}") String webBaseUrl) {
        this.listingService = listingService;
        this.propertyService = propertyService;
        this.propertyAgentRepository = propertyAgentRepository;
        this.agentRepository = agentRepository;
        this.clientRepository = clientRepository;
        this.tenantRepository = tenantRepository;
        this.activity = activity;
        this.webBaseUrl = webBaseUrl;
    }

    public SaleView view(UUID propertyId) {
        Property property = propertyService.get(propertyId);
        Listing listing = listingService.latest(propertyId)
                .orElseThrow(() -> new NotFoundException("Продаж для об'єкта ще не заведено: " + propertyId));
        SellerView seller = Optional.ofNullable(listing.getSellerClientId())
                .flatMap(clientRepository::findById).map(SaleService::sellerView).orElse(null);
        List<PricePoint> history = listingService.priceHistory(listing.getId()).stream()
                .map(h -> new PricePoint(h.getPrice(), h.getCurrency(), h.getChangedAt())).toList();
        List<String> blockers = new ArrayList<>();
        if (listing.getPrice() == null) {
            blockers.add("price");
        }
        if (property.getStatus() == Property.Status.DRAFT) {
            blockers.add("propertyDraft");
        }
        if (listing.getMandateValidUntil() != null && listing.getMandateValidUntil().isBefore(java.time.LocalDate.now())) {
            blockers.add("mandateExpired");
        }
        return new SaleView(listing.getId(), listing.getStatus(), listing.getPrice(), listing.getCurrency(), seller,
                listing.getMandateType(), listing.getMandateValidUntil(), listing.getCommissionPercent(),
                listing.getCommissionFixed(), listing.getAccessNotes(), listing.isHideExactAddress(),
                listing.getWithdrawnReason(), listing.getPublishedAt(), listing.getClosedAt(), history,
                agents(propertyId), listing.getStatus() == Listing.Status.ACTIVE ? publicUrl(property) : null, blockers);
    }

    @Transactional
    public SaleView update(UUID tenantId, UUID agentId, UUID propertyId, SaleForm form) {
        propertyService.get(propertyId); // чужий об'єкт → 404 до будь-якого запису
        if (form.sellerClientId() != null && clientRepository.findById(form.sellerClientId()).isEmpty()) {
            throw new ValidationException("Контакт не знайдено", List.of(new FieldError("sellerClientId", "invalid")));
        }
        listingService.ensureDraft(tenantId, propertyId, agentId);
        for (Change change : listingService.updateSale(propertyId, form)) {
            record(tenantId, propertyId, agentId, change, true);
        }
        return view(propertyId);
    }

    @Transactional
    public SaleView activate(UUID tenantId, UUID agentId, UUID propertyId) {
        Property property = propertyService.get(propertyId);
        if (property.getStatus() == Property.Status.DRAFT) {
            throw new ValidationException("Спершу завершіть картку об'єкта",
                    List.of(new FieldError("propertyDraft", "required")));
        }
        record(tenantId, propertyId, agentId, listingService.activate(propertyId), true);
        return view(propertyId);
    }

    @Transactional
    public SaleView withdraw(UUID tenantId, UUID agentId, UUID propertyId, String reason) {
        record(tenantId, propertyId, agentId, listingService.withdraw(propertyId, reason), true);
        return view(propertyId);
    }

    @Transactional
    public SaleView markSold(UUID tenantId, UUID agentId, UUID propertyId, BigDecimal finalPrice) {
        record(tenantId, propertyId, agentId, listingService.markSold(propertyId, finalPrice), true);
        return view(propertyId);
    }

    /** Відповідальні агенти: рівно один LEAD + будь-яка кількість CO_AGENT. */
    @Transactional
    public List<AgentView> setAgents(UUID tenantId, UUID actorAgentId, UUID propertyId, AgentsRequest request) {
        propertyService.get(propertyId);
        if (request.leadAgentId() == null) {
            throw new ValidationException("Потрібен головний агент", List.of(new FieldError("leadAgentId", "required")));
        }
        Set<UUID> wanted = new LinkedHashSet<>();
        wanted.add(request.leadAgentId());
        if (request.coAgentIds() != null) {
            wanted.addAll(request.coAgentIds());
        }
        Map<UUID, Agent> agents = agentRepository.findAllById(wanted).stream()
                .collect(Collectors.toMap(Agent::getId, Function.identity()));
        if (agents.size() != wanted.size()) {
            throw new ValidationException("Агента не знайдено", List.of(new FieldError("coAgentIds", "invalid")));
        }
        List<PropertyAgent> existing = propertyAgentRepository.findByPropertyIdOrderByCreatedAtAsc(propertyId);
        Set<UUID> before = existing.stream().map(PropertyAgent::getAgentId).collect(Collectors.toSet());
        existing.stream().filter(pa -> !wanted.contains(pa.getAgentId())).forEach(propertyAgentRepository::delete);
        for (UUID id : wanted) {
            PropertyAgent link = existing.stream().filter(pa -> pa.getAgentId().equals(id)).findFirst().orElseGet(() -> {
                PropertyAgent created = new PropertyAgent();
                created.setTenantId(tenantId);
                created.setPropertyId(propertyId);
                created.setAgentId(id);
                return created;
            });
            link.setRole(id.equals(request.leadAgentId()) ? PropertyAgent.Role.LEAD : PropertyAgent.Role.CO_AGENT);
            propertyAgentRepository.save(link);
        }
        if (!before.equals(wanted)) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("agents", wanted.stream().map(id -> agents.get(id).getFirstName() + " " + agents.get(id).getLastName()).toList());
            activity.record(tenantId, SubjectType.PROPERTY, propertyId, "AGENTS_CHANGED", payload, actorAgentId, false);
        }
        return agents(propertyId);
    }

    public List<AgentView> agents(UUID propertyId) {
        List<PropertyAgent> links = propertyAgentRepository.findByPropertyIdOrderByCreatedAtAsc(propertyId);
        Map<UUID, Agent> agents = agentRepository.findAllById(links.stream().map(PropertyAgent::getAgentId).toList())
                .stream().collect(Collectors.toMap(Agent::getId, Function.identity()));
        // LEAD завжди першим (enum у БД сортується як рядок — тому сортуємо тут).
        return links.stream().filter(l -> agents.containsKey(l.getAgentId()))
                .sorted(java.util.Comparator.comparing((PropertyAgent l) -> l.getRole() != PropertyAgent.Role.LEAD)
                        .thenComparing(PropertyAgent::getCreatedAt))
                .map(l -> agentView(agents.get(l.getAgentId()), l.getRole())).toList();
    }

    public List<TimelineEntry> timeline(UUID propertyId, boolean ownerOnly) {
        propertyService.get(propertyId);
        return toEntries(activity.timeline(SubjectType.PROPERTY, propertyId, ownerOnly));
    }

    public List<TimelineEntry> toEntries(List<ActivityEvent> events) {
        Set<UUID> actorIds = events.stream().map(ActivityEvent::getActorAgentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Agent> actors = actorIds.isEmpty() ? Map.of() : agentRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(Agent::getId, Function.identity()));
        return events.stream().map(e -> SaleDtos.entry(e, e.getActorAgentId() == null || !actors.containsKey(e.getActorAgentId())
                ? null : agentView(actors.get(e.getActorAgentId()), null))).toList();
    }

    public String publicUrl(Property property) {
        Tenant tenant = tenantRepository.findById(property.getTenantId())
                .orElseThrow(() -> new NotFoundException("Tenant не знайдено"));
        return webBaseUrl + "/p/" + tenant.getSlug() + "/" + property.getId();
    }

    private void record(UUID tenantId, UUID propertyId, UUID agentId, Change change, boolean ownerVisible) {
        activity.record(tenantId, SubjectType.PROPERTY, propertyId, change.event(), new HashMap<>(change.details()),
                agentId, ownerVisible);
    }

    static SellerView sellerView(Client c) {
        return new SellerView(c.getId(), c.getFirstName(), c.getLastName(), c.getPhone(), c.getEmail());
    }

    static AgentView agentView(Agent a, PropertyAgent.Role role) {
        return new AgentView(a.getId(), a.getFirstName(), a.getLastName(), a.getPhone(), a.getEmail(), role);
    }
}

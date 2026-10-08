package com.memphisreo.platform.api;

import com.memphisreo.activity.ActivityEvent.SubjectType;
import com.memphisreo.activity.ActivityRecorder;
import com.memphisreo.common.NotFoundException;
import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientForm;
import com.memphisreo.crm.ClientRepository;
import com.memphisreo.crm.ClientService;
import com.memphisreo.crm.ClientRequirement;
import com.memphisreo.crm.RequirementForm;
import com.memphisreo.listing.ListingService;
import com.memphisreo.platform.crm.ClientDtos.ClientDetails;
import com.memphisreo.platform.crm.ClientDtos.NoteRequest;
import com.memphisreo.platform.crm.ClientDtos.OwnedProperty;
import com.memphisreo.platform.crm.MatchingService;
import com.memphisreo.platform.sale.SaleDtos.TimelineEntry;
import com.memphisreo.platform.sale.SaleService;
import com.memphisreo.property.PropertyRepository;
import com.memphisreo.common.ValidationException;
import java.util.Set;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Контакти агенції: покупці й продавці (роль — у контексті угоди, не на записі). */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientRepository clientRepository;
    private final ClientService clientService;
    private final ActivityRecorder activity;
    private final org.springframework.transaction.support.TransactionTemplate tx;
    private final MatchingService matchingService;
    private final ListingService listingService;
    private final PropertyRepository propertyRepository;
    private final SaleService saleService;

    private static final Set<String> NOTE_KINDS = Set.of("CALL", "MESSAGE", "MEETING", "NOTE");

    public ClientController(ClientRepository clientRepository, ClientService clientService, ActivityRecorder activity,
                            org.springframework.transaction.support.TransactionTemplate tx, MatchingService matchingService,
                            ListingService listingService, PropertyRepository propertyRepository, SaleService saleService) {
        this.clientRepository = clientRepository;
        this.clientService = clientService;
        this.activity = activity;
        this.tx = tx;
        this.matchingService = matchingService;
        this.listingService = listingService;
        this.propertyRepository = propertyRepository;
        this.saleService = saleService;
    }

    /** Картка клієнта: профіль, запит, підібрані об'єкти, об'єкти у власності, журнал. */
    @GetMapping("/{id}/details")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_VIEW.name())")
    public ResponseEntity<ClientDetails> details(@PathVariable UUID id) {
        Client client = clientRepository.findById(id).orElseThrow(() -> new NotFoundException("Client не знайдено: " + id));
        List<OwnedProperty> owned = listingService.activeForSeller(id).stream()
                .map(l -> propertyRepository.findById(l.getPropertyId())
                        .map(p -> new OwnedProperty(p.getId(), p.getTitle(), l.getStatus().name())).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
        List<TimelineEntry> timeline = saleService.toEntries(activity.timeline(SubjectType.CLIENT, id, false));
        return ResponseEntity.ok(new ClientDetails(client, clientService.requirement(id).orElse(null),
                matchingService.propertiesForClient(id), owned, timeline));
    }

    @PutMapping("/{id}/requirement")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_MANAGE.name())")
    public ResponseEntity<ClientRequirement> saveRequirement(@AuthenticationPrincipal AuthenticatedAgent principal,
                                                             @PathVariable UUID id, @RequestBody RequirementForm form) {
        ClientRequirement saved = tx.execute(s -> {
            ClientRequirement r = clientService.saveRequirement(principal.tenantId(), id, form);
            activity.record(principal.tenantId(), SubjectType.CLIENT, id, "REQUIREMENT_UPDATED", java.util.Map.of(),
                    principal.agentId(), false);
            return r;
        });
        return ResponseEntity.ok(saved);
    }

    /** Журнал контактів — ручний запис агента (2 кліки). */
    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_MANAGE.name())")
    public ResponseEntity<List<TimelineEntry>> addNote(@AuthenticationPrincipal AuthenticatedAgent principal,
                                                       @PathVariable UUID id, @RequestBody NoteRequest request) {
        clientRepository.findById(id).orElseThrow(() -> new NotFoundException("Client не знайдено: " + id));
        String kind = request.kind() == null ? "NOTE" : request.kind().toUpperCase();
        if (!NOTE_KINDS.contains(kind) || request.note() == null || request.note().isBlank()) {
            throw new ValidationException("Порожній запис", List.of(new ValidationException.FieldError("note", "required")));
        }
        tx.executeWithoutResult(s -> activity.record(principal.tenantId(), SubjectType.CLIENT, id, "CONTACT_" + kind,
                java.util.Map.of("note", request.note().trim()), principal.agentId(), false));
        return ResponseEntity.ok(saleService.toEntries(activity.timeline(SubjectType.CLIENT, id, false)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_VIEW.name())")
    public ResponseEntity<List<Client>> list() {
        return ResponseEntity.ok(clientRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_VIEW.name())")
    public ResponseEntity<Client> get(@PathVariable UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Client не знайдено: " + id));
        return ResponseEntity.ok(client);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_MANAGE.name())")
    public ResponseEntity<Client> create(@AuthenticationPrincipal AuthenticatedAgent principal,
                                         @RequestBody ClientForm form) {
        Client client = tx.execute(s -> {
            Client created = clientService.create(principal.tenantId(), form);
            activity.record(principal.tenantId(), SubjectType.CLIENT, created.getId(), "CLIENT_CREATED",
                    java.util.Map.of("source", created.getSource().name()), principal.agentId(), false);
            return created;
        });
        return ResponseEntity.ok(client);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.memphisreo.security.rbac.Permission).CLIENT_MANAGE.name())")
    public ResponseEntity<Client> update(@PathVariable UUID id, @RequestBody ClientForm form) {
        return ResponseEntity.ok(clientService.update(id, form));
    }
}

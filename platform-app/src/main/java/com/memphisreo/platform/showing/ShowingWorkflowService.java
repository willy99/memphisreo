package com.memphisreo.platform.showing;

import com.memphisreo.activity.ActivityEvent.SubjectType;
import com.memphisreo.activity.ActivityRecorder;
import com.memphisreo.agent.Agent;
import com.memphisreo.agent.AgentRepository;
import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.ValidationException;
import com.memphisreo.common.ValidationException.FieldError;
import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientRepository;
import com.memphisreo.deal.FeedbackForm;
import com.memphisreo.deal.Showing;
import com.memphisreo.deal.ShowingForm;
import com.memphisreo.deal.ShowingService;
import com.memphisreo.listing.Listing;
import com.memphisreo.listing.ListingService;
import com.memphisreo.media.MediaService;
import com.memphisreo.platform.sale.SaleDtos.AgentView;
import com.memphisreo.platform.showing.ShowingDtos.ClientRef;
import com.memphisreo.platform.showing.ShowingDtos.PropertyRef;
import com.memphisreo.platform.showing.ShowingDtos.ShowingView;
import com.memphisreo.platform.showing.ShowingDtos.TaskView;
import com.memphisreo.property.Address;
import com.memphisreo.property.AddressRepository;
import com.memphisreo.property.Property;
import com.memphisreo.property.PropertyRepository;
import com.memphisreo.task.Task;
import com.memphisreo.task.TaskService;
import com.memphisreo.task.TaskService.NewTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Оркестрація показу: показ ↔ задача показу, події в таймлайнах об'єкта і
 * клієнта, задача "фідбек + власник" після показу, автозадача "передзвонити".
 * Власник бачить лише факт і узагальнений результат — без імен покупців.
 */
@Service
public class ShowingWorkflowService {

    static final Duration FEEDBACK_TASK_AFTER = Duration.ofHours(2);
    static final Duration FOLLOW_UP_AFTER = Duration.ofDays(2);

    private final ShowingService showingService;
    private final TaskService taskService;
    private final PropertyRepository propertyRepository;
    private final AddressRepository addressRepository;
    private final ClientRepository clientRepository;
    private final AgentRepository agentRepository;
    private final ListingService listingService;
    private final MediaService mediaService;
    private final ActivityRecorder activity;

    public ShowingWorkflowService(ShowingService showingService, TaskService taskService, PropertyRepository propertyRepository,
                                  AddressRepository addressRepository, ClientRepository clientRepository,
                                  AgentRepository agentRepository, ListingService listingService, MediaService mediaService,
                                  ActivityRecorder activity) {
        this.showingService = showingService;
        this.taskService = taskService;
        this.propertyRepository = propertyRepository;
        this.addressRepository = addressRepository;
        this.clientRepository = clientRepository;
        this.agentRepository = agentRepository;
        this.listingService = listingService;
        this.mediaService = mediaService;
        this.activity = activity;
    }

    @Transactional
    public ShowingView schedule(UUID tenantId, UUID actor, ShowingForm form) {
        ShowingForm normalized = new ShowingForm(form.propertyId(), form.clientIds(), form.agentId() == null ? actor : form.agentId(),
                form.scheduledAt(), form.durationMinutes(), form.notes(), form.ignoreAgentOverlap());
        Property property = requireProperty(normalized.propertyId());
        Listing listing = listingService.current(property.getId()).orElse(null);
        if (listing == null || !(listing.getStatus() == Listing.Status.ACTIVE || listing.getStatus() == Listing.Status.UNDER_OFFER)) {
            throw new ValidationException("Показ можливий лише для об'єкта у продажу", List.of(new FieldError("propertyId", "notForSale")));
        }
        requireClients(normalized.clientIds());
        requireAgent(normalized.agentId());

        Showing showing = showingService.schedule(tenantId, actor, normalized);
        taskService.create(tenantId, actor, new NewTask(Task.Kind.SHOWING, showingTitle(property, normalized.clientIds()),
                showing.getScheduledAt(), showing.getAgentId(), property.getId(), normalized.clientIds().get(0), showing.getId(), null));
        record(tenantId, actor, showing, "SHOWING_SCHEDULED", Map.of("scheduledAt", showing.getScheduledAt().toString()), true);
        return view(showing);
    }

    @Transactional
    public ShowingView reschedule(UUID tenantId, UUID actor, UUID showingId, ShowingForm form) {
        if (form.clientIds() != null) {
            requireClients(form.clientIds());
        }
        if (form.agentId() != null) {
            requireAgent(form.agentId());
        }
        Showing before = showingService.get(showingId);
        List<UUID> clientIds = form.clientIds() == null ? showingService.clientIds(showingId) : form.clientIds();
        Showing showing = showingService.reschedule(showingId, new ShowingForm(before.getPropertyId(), clientIds, form.agentId(),
                form.scheduledAt(), form.durationMinutes(), form.notes(), form.ignoreAgentOverlap()));
        Property property = requireProperty(showing.getPropertyId());
        taskService.syncShowingTask(showingId, showing.getScheduledAt(), Task.Status.OPEN, showingTitle(property, clientIds), showing.getAgentId());
        if (!Objects.equals(before.getScheduledAt(), showing.getScheduledAt())) {
            record(tenantId, actor, showing, "SHOWING_RESCHEDULED", Map.of("scheduledAt", showing.getScheduledAt().toString()), true);
        }
        return view(showing);
    }

    @Transactional
    public ShowingView confirm(UUID showingId) {
        return view(showingService.confirm(showingId));
    }

    @Transactional
    public ShowingView cancel(UUID tenantId, UUID actor, UUID showingId, String reason) {
        Showing showing = showingService.cancel(showingId, reason);
        taskService.syncShowingTask(showingId, showing.getScheduledAt(), Task.Status.CANCELLED,
                showingTitle(requireProperty(showing.getPropertyId()), showingService.clientIds(showingId)), showing.getAgentId());
        record(tenantId, actor, showing, "SHOWING_CANCELLED", reason == null || reason.isBlank() ? Map.of() : Map.of("reason", reason.trim()), true);
        return view(showing);
    }

    /** Відбувся → задача показу закрита, з'являється задача "фідбек + зв'язок із власником". */
    @Transactional
    public ShowingView complete(UUID tenantId, UUID actor, UUID showingId, boolean noShow) {
        Showing showing = showingService.complete(showingId, noShow);
        Property property = requireProperty(showing.getPropertyId());
        List<UUID> clientIds = showingService.clientIds(showingId);
        taskService.syncShowingTask(showingId, showing.getScheduledAt(), Task.Status.DONE, showingTitle(property, clientIds), showing.getAgentId());
        if (noShow) {
            record(tenantId, actor, showing, "SHOWING_NO_SHOW", Map.of(), true);
            taskService.create(tenantId, actor, new NewTask(Task.Kind.FOLLOW_UP, "Передзвонити: не прийшли на показ",
                    Instant.now().plus(Duration.ofHours(4)), showing.getAgentId(), property.getId(), clientIds.get(0), showingId, null));
        } else {
            record(tenantId, actor, showing, "SHOWING_COMPLETED", Map.of(), true);
            taskService.create(tenantId, actor, new NewTask(Task.Kind.FEEDBACK, "Фідбек після показу і зв'язок із власником",
                    showing.endsAt().plus(FEEDBACK_TASK_AFTER), showing.getAgentId(), property.getId(), clientIds.get(0), showingId, null));
        }
        return view(showing);
    }

    /** Фідбек: закриває задачу фідбеку; без обраного наступного кроку — автозадача "передзвонити" через 2 дні. */
    @Transactional
    public ShowingView feedback(UUID tenantId, UUID actor, UUID showingId, FeedbackForm form) {
        Showing showing = showingService.feedback(showingId, form);
        Property property = requireProperty(showing.getPropertyId());
        List<UUID> clientIds = showingService.clientIds(showingId);
        taskService.completeByShowingAndKind(showingId, Task.Kind.FEEDBACK);
        Map<String, Object> payload = new HashMap<>();
        payload.put("interest", showing.getInterest());
        if (showing.getObjection() != null) {
            payload.put("objection", showing.getObjection().name());
        }
        if (showing.getNextStep() != null) {
            payload.put("nextStep", showing.getNextStep().name());
        }
        record(tenantId, actor, showing, "SHOWING_FEEDBACK", payload, true);
        if (showing.getNextStep() == null || showing.getNextStep() == Showing.NextStep.WAITING) {
            taskService.create(tenantId, actor, new NewTask(Task.Kind.FOLLOW_UP, "Передзвонити клієнту після показу",
                    Instant.now().plus(FOLLOW_UP_AFTER), showing.getAgentId(), property.getId(), clientIds.get(0), showingId, null));
        } else if (showing.getNextStep() == Showing.NextStep.REPEAT_SHOWING) {
            taskService.create(tenantId, actor, new NewTask(Task.Kind.FOLLOW_UP, "Домовитись про повторний показ",
                    Instant.now().plus(Duration.ofDays(1)), showing.getAgentId(), property.getId(), clientIds.get(0), showingId, null));
        }
        return view(showing);
    }

    public ShowingView view(UUID showingId) {
        return view(showingService.get(showingId));
    }

    public List<ShowingView> forProperty(UUID propertyId) {
        requireProperty(propertyId);
        return views(showingService.forProperty(propertyId));
    }

    public List<ShowingView> forClient(UUID clientId) {
        return views(showingService.forClient(clientId));
    }

    public List<ShowingView> between(Instant from, Instant to, UUID agentId) {
        return views(showingService.between(from, to, agentId));
    }

    public List<TaskView> taskViews(List<Task> tasks) {
        Map<UUID, PropertyRef> properties = propertyRefs(tasks.stream().map(Task::getPropertyId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, Client> clients = clientRepository.findAllById(tasks.stream().map(Task::getClientId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Client::getId, Function.identity()));
        Map<UUID, Agent> agents = agentRepository.findAllById(tasks.stream().map(Task::getAssigneeAgentId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Agent::getId, Function.identity()));
        return tasks.stream().map(t -> new TaskView(t.getId(), t.getKind(), t.getTitle(), t.getStatus(), t.getDueAt(), t.getNote(),
                agentView(agents.get(t.getAssigneeAgentId())), properties.get(t.getPropertyId()),
                clientRef(clients.get(t.getClientId())), t.getShowingId(), t.getCompletedAt())).toList();
    }

    // ---------- helpers ----------

    private ShowingView view(Showing s) {
        return views(List.of(s)).get(0);
    }

    private List<ShowingView> views(List<Showing> showings) {
        if (showings.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<UUID>> clientIds = showingService.clientIdsBy(showings);
        Map<UUID, Client> clients = clientRepository.findAllById(clientIds.values().stream().flatMap(List::stream).distinct().toList())
                .stream().collect(Collectors.toMap(Client::getId, Function.identity()));
        Map<UUID, PropertyRef> properties = propertyRefs(showings.stream().map(Showing::getPropertyId).collect(Collectors.toSet()));
        Map<UUID, Agent> agents = agentRepository.findAllById(showings.stream().map(Showing::getAgentId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Agent::getId, Function.identity()));
        List<ShowingView> result = new ArrayList<>();
        for (Showing s : showings) {
            List<ClientRef> refs = clientIds.getOrDefault(s.getId(), List.of()).stream().map(clients::get).filter(Objects::nonNull)
                    .map(ShowingWorkflowService::clientRef).toList();
            result.add(new ShowingView(s.getId(), s.getStatus(), s.getScheduledAt(), s.getDurationMinutes(), s.getNotes(),
                    s.getCancelReason(), s.getInterest(), s.getObjection(), s.getFeedbackComment(), s.getNextStep(), s.getFeedbackAt(),
                    properties.get(s.getPropertyId()), refs, agentView(agents.get(s.getAgentId())), s.getCreatedAt()));
        }
        return result;
    }

    private Map<UUID, PropertyRef> propertyRefs(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Property> properties = propertyRepository.findAllById(ids).stream().collect(Collectors.toMap(Property::getId, Function.identity()));
        Map<UUID, Address> addresses = addressRepository.findAllById(properties.values().stream().map(Property::getAddressId).toList())
                .stream().collect(Collectors.toMap(Address::getId, Function.identity()));
        Map<UUID, String> covers = mediaService.coverThumbs(ids);
        Map<UUID, Listing> listings = listingService.currentByProperty(ids);
        Map<UUID, PropertyRef> result = new HashMap<>();
        for (Property p : properties.values()) {
            Address a = addresses.get(p.getAddressId());
            Listing l = listings.get(p.getId());
            result.put(p.getId(), new PropertyRef(p.getId(), p.getTitle(), a == null ? null : a.getCity(), a == null ? null : a.getDistrict(),
                    a == null ? null : a.getStreet(), a == null ? null : a.getHouseNumber(), covers.get(p.getId()),
                    l == null ? null : l.getAccessNotes()));
        }
        return result;
    }

    private void record(UUID tenantId, UUID actor, Showing showing, String type, Map<String, Object> payload, boolean ownerVisible) {
        activity.record(tenantId, SubjectType.PROPERTY, showing.getPropertyId(), type, payload, actor, ownerVisible);
        Map<String, Object> clientPayload = new HashMap<>(payload);
        clientPayload.put("propertyId", showing.getPropertyId().toString());
        for (UUID clientId : showingService.clientIds(showing.getId())) {
            activity.record(tenantId, SubjectType.CLIENT, clientId, type, clientPayload, actor, false);
        }
    }

    private String showingTitle(Property property, List<UUID> clientIds) {
        String who = clientRepository.findAllById(clientIds).stream().map(c -> c.getFirstName() + " " + c.getLastName())
                .collect(Collectors.joining(", "));
        return "Показ: " + (property.getTitle() != null ? property.getTitle() : "об'єкт") + (who.isEmpty() ? "" : " — " + who);
    }

    private Property requireProperty(UUID id) {
        return propertyRepository.findById(id).orElseThrow(() -> new NotFoundException("Об'єкт не знайдено: " + id));
    }

    private void requireClients(List<UUID> ids) {
        if (ids != null && clientRepository.findAllById(ids).size() != new java.util.HashSet<>(ids).size()) {
            throw new ValidationException("Клієнта не знайдено", List.of(new FieldError("clientIds", "invalid")));
        }
    }

    private void requireAgent(UUID id) {
        if (agentRepository.findById(id).isEmpty()) {
            throw new ValidationException("Агента не знайдено", List.of(new FieldError("agentId", "invalid")));
        }
    }

    static AgentView agentView(Agent a) {
        return a == null ? null : new AgentView(a.getId(), a.getFirstName(), a.getLastName(), a.getPhone(), a.getEmail(), null);
    }

    static ClientRef clientRef(Client c) {
        return c == null ? null : new ClientRef(c.getId(), c.getFirstName(), c.getLastName(), c.getPhone());
    }
}

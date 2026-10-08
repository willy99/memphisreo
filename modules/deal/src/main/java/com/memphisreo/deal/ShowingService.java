package com.memphisreo.deal;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.ValidationException;
import com.memphisreo.common.ValidationException.FieldError;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Покази: планування з перевіркою накладок, переходи статусів, фідбек.
 * Чи існують об'єкт/клієнти/агент — перевіряє композиційний корінь
 * (інші модулі); тут — лише правила самих показів (docs/sales-workflow.md §3).
 */
@Service
public class ShowingService {

    private static final Set<Showing.Status> ACTIVE = EnumSet.of(Showing.Status.SCHEDULED, Showing.Status.CONFIRMED);
    static final int MIN_DURATION = 15;
    static final int MAX_DURATION = 240;

    /** Накладка з іншим показом: чия (AGENT — можна проігнорувати, PROPERTY — ні). */
    public record Overlap(String kind, UUID showingId, Instant scheduledAt) {
    }

    private final ShowingRepository showingRepository;
    private final ShowingClientRepository showingClientRepository;

    public ShowingService(ShowingRepository showingRepository, ShowingClientRepository showingClientRepository) {
        this.showingRepository = showingRepository;
        this.showingClientRepository = showingClientRepository;
    }

    public Showing get(UUID id) {
        return showingRepository.findById(id).orElseThrow(() -> new NotFoundException("Показ не знайдено: " + id));
    }

    public List<UUID> clientIds(UUID showingId) {
        return showingClientRepository.findByShowingIdIn(List.of(showingId)).stream().map(ShowingClient::getClientId).toList();
    }

    public Map<UUID, List<UUID>> clientIdsBy(List<Showing> showings) {
        if (showings.isEmpty()) {
            return Map.of();
        }
        return showingClientRepository.findByShowingIdIn(showings.stream().map(Showing::getId).toList()).stream()
                .collect(Collectors.groupingBy(ShowingClient::getShowingId,
                        Collectors.mapping(ShowingClient::getClientId, Collectors.toList())));
    }

    public List<Showing> forProperty(UUID propertyId) {
        return showingRepository.findByPropertyIdOrderByScheduledAtDesc(propertyId);
    }

    public List<Showing> forClient(UUID clientId) {
        List<UUID> ids = showingClientRepository.findByClientId(clientId).stream().map(ShowingClient::getShowingId).toList();
        return ids.isEmpty() ? List.of() : showingRepository.findByIdInOrderByScheduledAtDesc(ids);
    }

    public List<Showing> between(Instant from, Instant to, UUID agentId) {
        return agentId == null ? showingRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(from, to)
                : showingRepository.findByAgentIdAndScheduledAtBetweenOrderByScheduledAtAsc(agentId, from, to);
    }

    /** Накладки для слота: по агенту (попередження) і по об'єкту (заборона). */
    public List<Overlap> overlaps(UUID propertyId, UUID agentId, Instant start, int durationMinutes, UUID exceptShowingId) {
        Instant end = start.plusSeconds(durationMinutes * 60L);
        List<Overlap> result = new ArrayList<>();
        for (Showing s : showingRepository.findByAgentIdAndStatusInAndScheduledAtLessThan(agentId, ACTIVE, end)) {
            if (!s.getId().equals(exceptShowingId) && s.endsAt().isAfter(start)) {
                result.add(new Overlap("AGENT", s.getId(), s.getScheduledAt()));
            }
        }
        for (Showing s : showingRepository.findByPropertyIdAndStatusInAndScheduledAtLessThan(propertyId, ACTIVE, end)) {
            if (!s.getId().equals(exceptShowingId) && s.endsAt().isAfter(start)) {
                result.add(new Overlap("PROPERTY", s.getId(), s.getScheduledAt()));
            }
        }
        return result;
    }

    @Transactional
    public Showing schedule(UUID tenantId, UUID createdBy, ShowingForm form) {
        validate(form);
        int duration = form.durationMinutes() == null ? 45 : form.durationMinutes();
        checkOverlaps(form.propertyId(), form.agentId(), form.scheduledAt(), duration, null, Boolean.TRUE.equals(form.ignoreAgentOverlap()));
        Showing showing = new Showing();
        showing.setTenantId(tenantId);
        showing.setPropertyId(form.propertyId());
        showing.setAgentId(form.agentId());
        showing.setScheduledAt(form.scheduledAt());
        showing.setDurationMinutes(duration);
        showing.setNotes(trim(form.notes()));
        showing.setCreatedByAgentId(createdBy);
        showing = showingRepository.save(showing);
        replaceClients(tenantId, showing.getId(), form.clientIds());
        return showing;
    }

    /** Перенесення / зміна учасників — лише для активного показу. */
    @Transactional
    public Showing reschedule(UUID showingId, ShowingForm form) {
        Showing showing = get(showingId);
        requireActive(showing);
        ShowingForm merged = new ShowingForm(showing.getPropertyId(), form.clientIds(), form.agentId() == null ? showing.getAgentId() : form.agentId(),
                form.scheduledAt() == null ? showing.getScheduledAt() : form.scheduledAt(),
                form.durationMinutes() == null ? showing.getDurationMinutes() : form.durationMinutes(), form.notes(), form.ignoreAgentOverlap());
        validate(merged);
        checkOverlaps(merged.propertyId(), merged.agentId(), merged.scheduledAt(), merged.durationMinutes(), showingId,
                Boolean.TRUE.equals(form.ignoreAgentOverlap()));
        showing.setAgentId(merged.agentId());
        showing.setScheduledAt(merged.scheduledAt());
        showing.setDurationMinutes(merged.durationMinutes());
        showing.setNotes(trim(form.notes()));
        showing.setStatus(Showing.Status.SCHEDULED); // перенесений показ потребує нового підтвердження
        showing.setUpdatedAt(Instant.now());
        replaceClients(showing.getTenantId(), showingId, merged.clientIds());
        return showing;
    }

    @Transactional
    public Showing confirm(UUID showingId) {
        Showing showing = get(showingId);
        requireStatus(showing, Showing.Status.SCHEDULED);
        showing.setStatus(Showing.Status.CONFIRMED);
        showing.setUpdatedAt(Instant.now());
        return showing;
    }

    @Transactional
    public Showing cancel(UUID showingId, String reason) {
        Showing showing = get(showingId);
        requireActive(showing);
        showing.setStatus(Showing.Status.CANCELLED);
        showing.setCancelReason(trim(reason));
        showing.setUpdatedAt(Instant.now());
        return showing;
    }

    /** Показ відбувся (або клієнт не прийшов). Фідбек — окремим кроком. */
    @Transactional
    public Showing complete(UUID showingId, boolean noShow) {
        Showing showing = get(showingId);
        requireActive(showing);
        showing.setStatus(noShow ? Showing.Status.NO_SHOW : Showing.Status.COMPLETED);
        showing.setUpdatedAt(Instant.now());
        return showing;
    }

    @Transactional
    public Showing feedback(UUID showingId, FeedbackForm form) {
        Showing showing = get(showingId);
        if (showing.getStatus() != Showing.Status.COMPLETED) {
            throw new ValidationException("Фідбек — лише для показу, що відбувся",
                    List.of(new FieldError("status", "invalidTransition")));
        }
        if (form.interest() == null || form.interest() < 1 || form.interest() > 5) {
            throw new ValidationException("Оцініть зацікавленість від 1 до 5", List.of(new FieldError("interest", "required")));
        }
        showing.setInterest(form.interest());
        showing.setObjection(form.objection());
        showing.setFeedbackComment(trim(form.comment()));
        showing.setNextStep(form.nextStep());
        showing.setFeedbackAt(Instant.now());
        showing.setUpdatedAt(Instant.now());
        return showing;
    }

    private void replaceClients(UUID tenantId, UUID showingId, List<UUID> clientIds) {
        showingClientRepository.deleteByShowingId(showingId);
        showingClientRepository.flush();
        for (UUID clientId : new java.util.LinkedHashSet<>(clientIds)) {
            ShowingClient link = new ShowingClient();
            link.setTenantId(tenantId);
            link.setShowingId(showingId);
            link.setClientId(clientId);
            showingClientRepository.save(link);
        }
    }

    private void checkOverlaps(UUID propertyId, UUID agentId, Instant start, int duration, UUID except, boolean ignoreAgent) {
        List<Overlap> overlaps = overlaps(propertyId, agentId, start, duration, except);
        List<FieldError> errors = new ArrayList<>();
        if (overlaps.stream().anyMatch(o -> o.kind().equals("PROPERTY"))) {
            errors.add(new FieldError("scheduledAt", "propertyBusy"));
        } else if (!ignoreAgent && overlaps.stream().anyMatch(o -> o.kind().equals("AGENT"))) {
            errors.add(new FieldError("scheduledAt", "agentBusy"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Час зайнятий", errors);
        }
    }

    private static void validate(ShowingForm form) {
        List<FieldError> errors = new ArrayList<>();
        if (form.propertyId() == null) {
            errors.add(new FieldError("propertyId", "required"));
        }
        if (form.clientIds() == null || form.clientIds().isEmpty()) {
            errors.add(new FieldError("clientIds", "required"));
        }
        if (form.agentId() == null) {
            errors.add(new FieldError("agentId", "required"));
        }
        if (form.scheduledAt() == null) {
            errors.add(new FieldError("scheduledAt", "required"));
        }
        if (form.durationMinutes() != null && (form.durationMinutes() < MIN_DURATION || form.durationMinutes() > MAX_DURATION)) {
            errors.add(new FieldError("durationMinutes", "outOfRange"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Заповніть показ", errors);
        }
    }

    private static void requireActive(Showing showing) {
        if (!showing.isActive()) {
            throw new ValidationException("Показ уже " + showing.getStatus(), List.of(new FieldError("status", "invalidTransition")));
        }
    }

    private static void requireStatus(Showing showing, Showing.Status expected) {
        if (showing.getStatus() != expected) {
            throw new ValidationException("Неможливий перехід зі статусу " + showing.getStatus(),
                    List.of(new FieldError("status", "invalidTransition")));
        }
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

package com.memphisreo.platform.showing;

import com.memphisreo.common.ForbiddenException;
import com.memphisreo.common.TenantContext;
import com.memphisreo.deal.CalendarToken;
import com.memphisreo.deal.CalendarTokenRepository;
import com.memphisreo.deal.Showing;
import com.memphisreo.platform.showing.ShowingDtos.ShowingView;
import com.memphisreo.platform.showing.ShowingDtos.TaskView;
import com.memphisreo.task.Task;
import com.memphisreo.task.TaskService;
import com.memphisreo.tenant.Tenant;
import com.memphisreo.tenant.TenantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Календар: покази + задачі за період, iCal-підписка (посилання з токеном,
 * яке агент один раз додає в Google Calendar / iPhone) і .ics-запрошення
 * для клієнта. Формат — RFC 5545, мінімальний.
 */
@Service
public class CalendarService {

    private static final DateTimeFormatter ICAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShowingWorkflowService showings;
    private final TaskService taskService;
    private final CalendarTokenRepository tokenRepository;
    private final TenantRepository tenantRepository;
    private final String apiBaseUrl;

    public CalendarService(ShowingWorkflowService showings, TaskService taskService, CalendarTokenRepository tokenRepository,
                           TenantRepository tenantRepository, @Value("${memphisreo.api-base-url}") String apiBaseUrl) {
        this.showings = showings;
        this.taskService = taskService;
        this.tokenRepository = tokenRepository;
        this.tenantRepository = tenantRepository;
        this.apiBaseUrl = apiBaseUrl;
    }

    public ShowingDtos.CalendarView calendar(Instant from, Instant to, UUID agentId) {
        List<ShowingView> s = showings.between(from, to, agentId);
        List<Task> tasks = taskService.between(from, to, agentId).stream().filter(t -> t.getKind() != Task.Kind.SHOWING).toList();
        return new ShowingDtos.CalendarView(s, showings.taskViews(tasks));
    }

    /** Посилання підписки: створюється один раз на агента; повторний виклик повертає нове (старе перестає діяти). */
    @Transactional
    public String subscriptionUrl(UUID tenantId, UUID agentId, boolean rotate) {
        CalendarToken existing = tokenRepository.findByAgentId(agentId).orElse(null);
        if (existing != null && !rotate) {
            // Токен не зберігається у відкритому вигляді — без ротації повернути посилання неможливо.
            return null;
        }
        if (existing != null) {
            tokenRepository.delete(existing);
            tokenRepository.flush();
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        CalendarToken ct = new CalendarToken();
        ct.setTenantId(tenantId);
        ct.setAgentId(agentId);
        ct.setTokenHash(sha256(token));
        tokenRepository.save(ct);
        return apiBaseUrl + "/api/public/calendar/" + token + ".ics";
    }

    public boolean hasSubscription(UUID agentId) {
        return tokenRepository.findByAgentId(agentId).isPresent();
    }

    /** Публічний фід за токеном: tenant невідомий до перевірки — шукаємо по всіх агенціях. */
    public String feed(String token) {
        String hash = sha256(token == null ? "" : token.trim());
        for (Tenant tenant : tenantRepository.findAll()) {
            String ics = TenantContext.callAs(tenant.getId(), () -> tokenRepository.findByTokenHash(hash).map(ct -> {
                Instant from = Instant.now().minus(Duration.ofDays(30));
                Instant to = Instant.now().plus(Duration.ofDays(120));
                List<ShowingView> items = showings.between(from, to, ct.getAgentId()).stream()
                        .filter(s -> s.status() != Showing.Status.CANCELLED).toList();
                List<Task> tasks = taskService.between(from, to, ct.getAgentId()).stream()
                        .filter(t -> t.getKind() != Task.Kind.SHOWING && t.getStatus() == Task.Status.OPEN).toList();
                return calendarText(tenant.getName(), items, showings.taskViews(tasks));
            }).orElse(null));
            if (ics != null) {
                return ics;
            }
        }
        throw new ForbiddenException("Посилання календаря недійсне");
    }

    /** .ics одного показу для клієнта (без телефонів інших учасників). */
    public String invite(ShowingView s) {
        return wrap("Memphis", List.of(event(s, false)));
    }

    private String calendarText(String agencyName, List<ShowingView> items, List<TaskView> tasks) {
        List<String> events = new java.util.ArrayList<>();
        items.forEach(s -> events.add(event(s, true)));
        tasks.forEach(t -> events.add(taskEvent(t)));
        return wrap(agencyName, events);
    }

    private static String event(ShowingView s, boolean internal) {
        String where = s.property() == null ? "" : List.of(s.property().street(), s.property().houseNumber(), s.property().district(), s.property().city())
                .stream().filter(x -> x != null && !x.isBlank()).collect(Collectors.joining(", "));
        String who = s.clients().stream().map(c -> c.firstName() + " " + c.lastName() + (internal && c.phone() != null ? " " + c.phone() : ""))
                .collect(Collectors.joining(", "));
        String summary = "Показ: " + (s.property() != null && s.property().title() != null ? s.property().title() : "об'єкт");
        StringBuilder desc = new StringBuilder();
        if (!who.isEmpty()) {
            desc.append(internal ? "Клієнти: " : "Агент: ").append(internal ? who : agentLine(s)).append("\\n");
        }
        if (internal && s.property() != null && s.property().accessNotes() != null) {
            desc.append("Доступ: ").append(s.property().accessNotes()).append("\\n");
        }
        if (s.notes() != null) {
            desc.append(s.notes());
        }
        return "BEGIN:VEVENT\r\nUID:showing-" + s.id() + "@memphis\r\nDTSTAMP:" + ICAL.format(Instant.now())
                + "\r\nDTSTART:" + ICAL.format(s.scheduledAt()) + "\r\nDTEND:" + ICAL.format(s.scheduledAt().plusSeconds(s.durationMinutes() * 60L))
                + "\r\nSUMMARY:" + escape(summary) + "\r\nLOCATION:" + escape(where) + "\r\nDESCRIPTION:" + escape(desc.toString())
                + (s.status() == Showing.Status.CANCELLED ? "\r\nSTATUS:CANCELLED" : "") + "\r\nEND:VEVENT";
    }

    private static String agentLine(ShowingView s) {
        return s.agent() == null ? "" : s.agent().firstName() + " " + s.agent().lastName() + (s.agent().phone() != null ? " " + s.agent().phone() : "");
    }

    private static String taskEvent(TaskView t) {
        return "BEGIN:VEVENT\r\nUID:task-" + t.id() + "@memphis\r\nDTSTAMP:" + ICAL.format(Instant.now())
                + "\r\nDTSTART:" + ICAL.format(t.dueAt()) + "\r\nDTEND:" + ICAL.format(t.dueAt().plusSeconds(1800))
                + "\r\nSUMMARY:" + escape("Задача: " + t.title()) + "\r\nEND:VEVENT";
    }

    private static String wrap(String name, List<String> events) {
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Memphis//Calendar//UK\r\nCALSCALE:GREGORIAN\r\nX-WR-CALNAME:"
                + escape(name) + "\r\n" + String.join("\r\n", events) + (events.isEmpty() ? "" : "\r\n") + "END:VCALENDAR\r\n";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n");
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

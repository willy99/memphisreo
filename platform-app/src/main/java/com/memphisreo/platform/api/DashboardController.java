package com.memphisreo.platform.api;

import com.memphisreo.platform.dashboard.TenantStats;
import com.memphisreo.platform.dashboard.TenantStatsService;
import com.memphisreo.platform.showing.CalendarService;
import com.memphisreo.platform.showing.ShowingDtos.CalendarView;
import com.memphisreo.platform.showing.ShowingDtos.TodaySummary;
import com.memphisreo.platform.showing.ShowingWorkflowService;
import com.memphisreo.deal.Showing;
import com.memphisreo.inquiry.Inquiry;
import com.memphisreo.inquiry.InquiryRepository;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import com.memphisreo.task.TaskService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Головна сторінка агенції. Будь-який автентифікований агент бачить загальні цифри своєї агенції. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final TenantStatsService tenantStatsService;
    private final CalendarService calendarService;
    private final ShowingWorkflowService workflow;
    private final TaskService taskService;
    private final InquiryRepository inquiryRepository;

    public DashboardController(TenantStatsService tenantStatsService, CalendarService calendarService,
                               ShowingWorkflowService workflow, TaskService taskService, InquiryRepository inquiryRepository) {
        this.tenantStatsService = tenantStatsService;
        this.calendarService = calendarService;
        this.workflow = workflow;
        this.taskService = taskService;
        this.inquiryRepository = inquiryRepository;
    }

    /** "Мій день": покази й задачі на сьогодні (у часовому поясі клієнта), протерміновані, нові заявки. */
    @GetMapping("/today")
    public ResponseEntity<TodaySummary> today(@AuthenticationPrincipal AuthenticatedAgent p,
                                              @RequestParam(defaultValue = "Europe/Kyiv") String zone) {
        ZoneId zoneId = ZoneId.of(zone);
        Instant start = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant();
        Instant end = start.plusSeconds(86400);
        CalendarView day = calendarService.calendar(start, end, p.agentId());
        var showings = day.showings().stream().filter(s -> s.status() != Showing.Status.CANCELLED).toList();
        var dueTasks = day.tasks().stream().filter(t -> t.status() == com.memphisreo.task.Task.Status.OPEN).toList();
        long overdue = taskService.overdue(p.agentId(), start).stream().filter(t -> t.getKind() != com.memphisreo.task.Task.Kind.SHOWING).count();
        long inquiries = inquiryRepository.findAll().stream().filter(i -> i.getStatus() == Inquiry.Status.NEW).count();
        return ResponseEntity.ok(new TodaySummary(showings.size(), dueTasks.size(), overdue, inquiries, showings, dueTasks));
    }

    @GetMapping("/stats")
    public ResponseEntity<TenantStats> stats() {
        return ResponseEntity.ok(tenantStatsService.currentTenantStats());
    }
}

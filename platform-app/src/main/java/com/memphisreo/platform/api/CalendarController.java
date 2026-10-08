package com.memphisreo.platform.api;

import com.memphisreo.platform.showing.CalendarService;
import com.memphisreo.platform.showing.ShowingDtos.CalendarSubscription;
import com.memphisreo.platform.showing.ShowingDtos.CalendarView;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    /** Покази + задачі за період; без agentId — уся агенція (режим керівника). */
    @GetMapping("/api/calendar")
    public ResponseEntity<CalendarView> calendar(@RequestParam Instant from, @RequestParam Instant to,
                                                 @RequestParam(required = false) UUID agentId) {
        return ResponseEntity.ok(calendarService.calendar(from, to, agentId));
    }

    /** Посилання iCal-підписки поточного агента (нове при кожному виклику — старе перестає діяти). */
    @PostMapping("/api/calendar/subscription")
    public ResponseEntity<CalendarSubscription> subscription(@AuthenticationPrincipal AuthenticatedAgent p) {
        return ResponseEntity.ok(new CalendarSubscription(calendarService.subscriptionUrl(p.tenantId(), p.agentId(), true)));
    }

    @GetMapping(value = "/api/public/calendar/{token}.ics", produces = "text/calendar")
    public ResponseEntity<String> feed(@PathVariable String token) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar; charset=utf-8")).body(calendarService.feed(token));
    }
}

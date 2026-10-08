package com.memphisreo.platform.api;

import com.memphisreo.deal.FeedbackForm;
import com.memphisreo.deal.ShowingForm;
import com.memphisreo.platform.showing.CalendarService;
import com.memphisreo.platform.showing.ShowingDtos.CancelRequest;
import com.memphisreo.platform.showing.ShowingDtos.CompleteRequest;
import com.memphisreo.platform.showing.ShowingDtos.ShowingView;
import com.memphisreo.platform.showing.ShowingWorkflowService;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Покази. Дозволи — ті самі, що для лідів: будь-який агент агенції планує й веде покази. */
@RestController
public class ShowingController {

    private static final String VIEW = "hasAuthority(T(com.memphisreo.security.rbac.Permission).LEAD_VIEW.name())";
    private static final String MANAGE = "hasAuthority(T(com.memphisreo.security.rbac.Permission).LEAD_MANAGE.name())";

    private final ShowingWorkflowService workflow;
    private final CalendarService calendarService;

    public ShowingController(ShowingWorkflowService workflow, CalendarService calendarService) {
        this.workflow = workflow;
        this.calendarService = calendarService;
    }

    @PostMapping("/api/showings")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ShowingView> schedule(@AuthenticationPrincipal AuthenticatedAgent p, @RequestBody ShowingForm form) {
        return ResponseEntity.ok(workflow.schedule(p.tenantId(), p.agentId(), form));
    }

    @GetMapping("/api/showings/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ShowingView> get(@PathVariable UUID id) {
        return ResponseEntity.ok(workflow.view(id));
    }

    @PutMapping("/api/showings/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ShowingView> reschedule(@AuthenticationPrincipal AuthenticatedAgent p, @PathVariable UUID id,
                                                  @RequestBody ShowingForm form) {
        return ResponseEntity.ok(workflow.reschedule(p.tenantId(), p.agentId(), id, form));
    }

    @PostMapping("/api/showings/{id}/confirm")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ShowingView> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(workflow.confirm(id));
    }

    @PostMapping("/api/showings/{id}/cancel")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ShowingView> cancel(@AuthenticationPrincipal AuthenticatedAgent p, @PathVariable UUID id,
                                              @RequestBody(required = false) CancelRequest request) {
        return ResponseEntity.ok(workflow.cancel(p.tenantId(), p.agentId(), id, request == null ? null : request.reason()));
    }

    @PostMapping("/api/showings/{id}/complete")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ShowingView> complete(@AuthenticationPrincipal AuthenticatedAgent p, @PathVariable UUID id,
                                                @RequestBody(required = false) CompleteRequest request) {
        return ResponseEntity.ok(workflow.complete(p.tenantId(), p.agentId(), id, request != null && Boolean.TRUE.equals(request.noShow())));
    }

    @PostMapping("/api/showings/{id}/feedback")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ShowingView> feedback(@AuthenticationPrincipal AuthenticatedAgent p, @PathVariable UUID id,
                                                @RequestBody FeedbackForm form) {
        return ResponseEntity.ok(workflow.feedback(p.tenantId(), p.agentId(), id, form));
    }

    /** .ics для клієнта — агент надсилає файл у месенджер. */
    @GetMapping(value = "/api/showings/{id}/invite.ics", produces = "text/calendar")
    @PreAuthorize(VIEW)
    public ResponseEntity<String> invite(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .header("Content-Disposition", "attachment; filename=\"showing.ics\"")
                .body(calendarService.invite(workflow.view(id)));
    }

    @GetMapping("/api/properties/{propertyId}/showings")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<ShowingView>> forProperty(@PathVariable UUID propertyId) {
        return ResponseEntity.ok(workflow.forProperty(propertyId));
    }

    @GetMapping("/api/clients/{clientId}/showings")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<ShowingView>> forClient(@PathVariable UUID clientId) {
        return ResponseEntity.ok(workflow.forClient(clientId));
    }
}

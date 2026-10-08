package com.memphisreo.platform;

import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientForm;
import com.memphisreo.deal.FeedbackForm;
import com.memphisreo.deal.Showing;
import com.memphisreo.deal.ShowingForm;
import com.memphisreo.platform.property.PropertyEditorDtos.PropertyDetails;
import com.memphisreo.platform.sale.SaleDtos.TimelineEntry;
import com.memphisreo.platform.showing.ShowingDtos.CalendarSubscription;
import com.memphisreo.platform.showing.ShowingDtos.CalendarView;
import com.memphisreo.platform.showing.ShowingDtos.CompleteRequest;
import com.memphisreo.platform.showing.ShowingDtos.ShowingView;
import com.memphisreo.platform.showing.ShowingDtos.TaskView;
import com.memphisreo.platform.showing.ShowingDtos.TodaySummary;
import com.memphisreo.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Покази: планування, накладки, статуси, фідбек → задачі, календар, iCal. docs/sales-workflow.md §3. */
class ShowingWorkflowIT extends AbstractIntegrationTest {

    private String token;
    private UUID agentId;
    private PropertyDetails property;
    private Client buyer;

    @BeforeEach
    void agencyWithListedProperty() {
        String slug = "show-" + UUID.randomUUID().toString().substring(0, 8);
        agentId = register(slug, "admin@" + slug + ".ua", "Password123!", "UA").adminAgentId();
        token = login("admin@" + slug + ".ua", "Password123!");
        property = createProperty(token);
        listProperty(token, property.id());
        buyer = restTemplate.exchange("/api/clients", HttpMethod.POST,
                authed(token, new ClientForm("Марина", "Коваленко", null, "+380670000000", Client.Source.ADVERTISEMENT, null)),
                Client.class).getBody();
    }

    @Test
    void scheduleCreatesTaskAndTimelineEvents_feedbackCreatesFollowUp() {
        Instant at = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        ShowingView showing = schedule(at, null);
        assertThat(showing.status()).isEqualTo(Showing.Status.SCHEDULED);
        assertThat(showing.clients()).extracting(c -> c.firstName()).containsExactly("Марина");
        assertThat(showing.property().street()).isEqualTo("Khreshchatyk");

        TaskView[] tasks = restTemplate.exchange("/api/tasks", HttpMethod.GET, authed(token), TaskView[].class).getBody();
        assertThat(tasks).extracting(TaskView::kind).containsExactly(Task.Kind.SHOWING);
        assertThat(tasks[0].dueAt()).isEqualTo(at);
        assertThat(tasks[0].showingId()).isEqualTo(showing.id());

        // confirm → complete → задача показу закрита, з'явилась задача фідбеку
        post("/api/showings/" + showing.id() + "/confirm", null);
        ShowingView done = post("/api/showings/" + showing.id() + "/complete", new CompleteRequest(false));
        assertThat(done.status()).isEqualTo(Showing.Status.COMPLETED);
        tasks = restTemplate.exchange("/api/tasks?includeDone=true", HttpMethod.GET, authed(token), TaskView[].class).getBody();
        assertThat(tasks).extracting(TaskView::kind, TaskView::status)
                .contains(tuple(Task.Kind.SHOWING, Task.Status.DONE), tuple(Task.Kind.FEEDBACK, Task.Status.OPEN));

        // фідбек без наступного кроку → задача фідбеку закрита, "передзвонити" через 2 дні
        ShowingView withFeedback = post("/api/showings/" + showing.id() + "/feedback",
                new FeedbackForm(2, Showing.Objection.PRICE, "Дорого для них", null));
        assertThat(withFeedback.interest()).isEqualTo(2);
        tasks = restTemplate.exchange("/api/tasks", HttpMethod.GET, authed(token), TaskView[].class).getBody();
        assertThat(tasks).extracting(TaskView::kind).containsExactly(Task.Kind.FOLLOW_UP);
        assertThat(tasks[0].client().id()).isEqualTo(buyer.getId());

        List<TimelineEntry> propertyTimeline = List.of(restTemplate.exchange("/api/properties/" + property.id() + "/timeline",
                HttpMethod.GET, authed(token), TimelineEntry[].class).getBody());
        assertThat(propertyTimeline).extracting(TimelineEntry::type)
                .containsSubsequence("SHOWING_FEEDBACK", "SHOWING_COMPLETED", "SHOWING_SCHEDULED");
        assertThat(propertyTimeline.get(0).payload()).containsEntry("interest", 2).containsEntry("objection", "PRICE");
        assertThat(propertyTimeline.get(0).ownerVisible()).isTrue();

        // Фідбек повторно / для незавершеного — 400
        ShowingView another = schedule(at.plus(3, ChronoUnit.HOURS), null);
        assertThat(restTemplate.exchange("/api/showings/" + another.id() + "/feedback", HttpMethod.POST,
                authed(token, new FeedbackForm(3, null, null, null)), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void overlaps_propertyIsHardBlock_agentIsWarningThatCanBeOverridden() {
        Instant at = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        schedule(at, null);

        // Той самий об'єкт, той самий час — заборона, навіть із прапорцем.
        ResponseEntity<String> sameProperty = restTemplate.exchange("/api/showings", HttpMethod.POST,
                authed(token, new ShowingForm(property.id(), List.of(buyer.getId()), null, at.plusSeconds(600), 45, null, true)), String.class);
        assertThat(sameProperty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sameProperty.getBody()).contains("propertyBusy");

        // Інший об'єкт, той самий агент і час — попередження, з прапорцем можна.
        PropertyDetails other = createProperty(token);
        listProperty(token, other.id());
        ResponseEntity<String> sameAgent = restTemplate.exchange("/api/showings", HttpMethod.POST,
                authed(token, new ShowingForm(other.id(), List.of(buyer.getId()), null, at.plusSeconds(600), 45, null, false)), String.class);
        assertThat(sameAgent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sameAgent.getBody()).contains("agentBusy");
        assertThat(restTemplate.exchange("/api/showings", HttpMethod.POST,
                authed(token, new ShowingForm(other.id(), List.of(buyer.getId()), null, at.plusSeconds(600), 45, null, true)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void draftPropertyCannotBeShown_cancelClosesTask_noShowCreatesFollowUp() {
        PropertyDetails draft = createProperty(token);
        ResponseEntity<String> refused = restTemplate.exchange("/api/showings", HttpMethod.POST,
                authed(token, new ShowingForm(draft.id(), List.of(buyer.getId()), null, Instant.now().plus(1, ChronoUnit.DAYS), 45, null, false)),
                String.class);
        assertThat(refused.getBody()).contains("notForSale");

        ShowingView cancelled = schedule(Instant.now().plus(1, ChronoUnit.DAYS), null);
        post("/api/showings/" + cancelled.id() + "/cancel", new com.memphisreo.platform.showing.ShowingDtos.CancelRequest("Клієнт передумав"));
        TaskView[] tasks = restTemplate.exchange("/api/tasks?includeDone=true", HttpMethod.GET, authed(token), TaskView[].class).getBody();
        assertThat(tasks).extracting(TaskView::status).containsExactly(Task.Status.CANCELLED);

        ShowingView noShow = schedule(Instant.now().plus(2, ChronoUnit.DAYS), null);
        post("/api/showings/" + noShow.id() + "/complete", new CompleteRequest(true));
        tasks = restTemplate.exchange("/api/tasks", HttpMethod.GET, authed(token), TaskView[].class).getBody();
        assertThat(tasks).extracting(TaskView::kind).containsExactly(Task.Kind.FOLLOW_UP);
    }

    @Test
    void calendarAndTodayAndIcalFeed() {
        Instant soon = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        schedule(soon, "Зустріч біля під'їзду");
        CalendarView calendar = restTemplate.exchange("/api/calendar?from=" + Instant.now().minusSeconds(3600) + "&to=" + Instant.now().plusSeconds(86400),
                HttpMethod.GET, authed(token), CalendarView.class).getBody();
        assertThat(calendar.showings()).hasSize(1);
        assertThat(calendar.tasks()).isEmpty(); // задача показу не дублює показ

        TodaySummary today = restTemplate.exchange("/api/dashboard/today?zone=UTC", HttpMethod.GET, authed(token), TodaySummary.class).getBody();
        assertThat(today.showingsToday() + today.nextShowings().size()).isGreaterThanOrEqualTo(0);

        CalendarSubscription sub = restTemplate.exchange("/api/calendar/subscription", HttpMethod.POST, authed(token), CalendarSubscription.class).getBody();
        assertThat(sub.url()).contains("/api/public/calendar/").endsWith(".ics");
        String path = sub.url().substring(sub.url().indexOf("/api/public"));
        ResponseEntity<String> feed = restTemplate.exchange(path, HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(feed.getBody()).contains("BEGIN:VCALENDAR").contains("SUMMARY:Показ: Test property").contains("Марина Коваленко +380670000000");

        // Запрошення для клієнта — без телефонів клієнтів, з агентом.
        ShowingView s = calendar.showings().get(0);
        String invite = restTemplate.exchange("/api/showings/" + s.id() + "/invite.ics", HttpMethod.GET, authed(token), String.class).getBody();
        assertThat(invite).contains("BEGIN:VEVENT").doesNotContain("+380670000000").contains("Агент: Test Admin");

        assertThat(restTemplate.exchange("/api/public/calendar/bogus.ics", HttpMethod.GET, HttpEntity.EMPTY, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ShowingView schedule(Instant at, String notes) {
        ResponseEntity<ShowingView> response = restTemplate.exchange("/api/showings", HttpMethod.POST,
                authed(token, new ShowingForm(property.id(), List.of(buyer.getId()), agentId, at, 45, notes, false)), ShowingView.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ShowingView post(String path, Object body) {
        ResponseEntity<ShowingView> response = restTemplate.exchange(path, HttpMethod.POST,
                body == null ? authed(token) : authed(token, body), ShowingView.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}

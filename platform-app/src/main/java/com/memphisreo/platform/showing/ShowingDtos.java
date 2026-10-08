package com.memphisreo.platform.showing;

import com.memphisreo.deal.Showing;
import com.memphisreo.platform.sale.SaleDtos.AgentView;
import com.memphisreo.task.Task;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ShowingDtos {

    private ShowingDtos() {
    }

    public record PropertyRef(UUID id, String title, String city, String district, String street, String houseNumber,
                              String coverUrl, String accessNotes) {
    }

    public record ClientRef(UUID id, String firstName, String lastName, String phone) {
    }

    public record ShowingView(UUID id, Showing.Status status, Instant scheduledAt, int durationMinutes, String notes,
                              String cancelReason, Integer interest, Showing.Objection objection, String feedbackComment,
                              Showing.NextStep nextStep, Instant feedbackAt, PropertyRef property, List<ClientRef> clients,
                              AgentView agent, Instant createdAt) {
    }

    public record TaskView(UUID id, Task.Kind kind, String title, Task.Status status, Instant dueAt, String note,
                           AgentView assignee, PropertyRef property, ClientRef client, UUID showingId, Instant completedAt) {
    }

    public record CancelRequest(String reason) {
    }

    public record CompleteRequest(Boolean noShow) {
    }

    public record NewTaskRequest(String title, Instant dueAt, UUID assigneeAgentId, UUID propertyId, UUID clientId, String note) {
    }

    /** Календар за період: покази + задачі (крім задач-показів, щоб не дублювати). */
    public record CalendarView(List<ShowingView> showings, List<TaskView> tasks) {
    }

    public record CalendarSubscription(String url) {
    }

    public record TodaySummary(long showingsToday, long tasksToday, long overdueTasks, long newInquiries,
                               List<ShowingView> nextShowings, List<TaskView> dueTasks) {
    }
}

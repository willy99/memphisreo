package com.memphisreo.task;

import com.memphisreo.common.NotFoundException;
import com.memphisreo.common.ValidationException;
import com.memphisreo.common.ValidationException.FieldError;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TaskService {

    public record NewTask(Task.Kind kind, String title, Instant dueAt, UUID assigneeAgentId, UUID propertyId,
                          UUID clientId, UUID showingId, String note) {
    }

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public Task get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Задачу не знайдено: " + id));
    }

    @Transactional
    public Task create(UUID tenantId, UUID createdBy, NewTask t) {
        if (t.title() == null || t.title().isBlank()) {
            throw new ValidationException("Назва задачі", List.of(new FieldError("title", "required")));
        }
        if (t.dueAt() == null) {
            throw new ValidationException("Дедлайн", List.of(new FieldError("dueAt", "required")));
        }
        Task task = new Task();
        task.setTenantId(tenantId);
        task.setKind(t.kind() == null ? Task.Kind.CUSTOM : t.kind());
        task.setTitle(t.title().trim());
        task.setDueAt(t.dueAt());
        task.setAssigneeAgentId(t.assigneeAgentId() == null ? createdBy : t.assigneeAgentId());
        task.setPropertyId(t.propertyId());
        task.setClientId(t.clientId());
        task.setShowingId(t.showingId());
        task.setNote(t.note() == null || t.note().isBlank() ? null : t.note().trim());
        task.setCreatedByAgentId(createdBy);
        return repository.save(task);
    }

    @Transactional
    public Task complete(UUID id) {
        Task task = get(id);
        task.setStatus(Task.Status.DONE);
        task.setCompletedAt(Instant.now());
        return task;
    }

    @Transactional
    public Task reopen(UUID id) {
        Task task = get(id);
        task.setStatus(Task.Status.OPEN);
        task.setCompletedAt(null);
        return task;
    }

    @Transactional
    public Task cancel(UUID id) {
        Task task = get(id);
        task.setStatus(Task.Status.CANCELLED);
        return task;
    }

    /** Задача показу слідує за показом: перенесення, скасування, завершення. */
    @Transactional
    public void syncShowingTask(UUID showingId, Instant dueAt, Task.Status status, String title, UUID assignee) {
        for (Task task : repository.findByShowingIdAndKind(showingId, Task.Kind.SHOWING)) {
            task.setDueAt(dueAt);
            task.setStatus(status);
            task.setTitle(title);
            task.setAssigneeAgentId(assignee);
            task.setCompletedAt(status == Task.Status.DONE ? Instant.now() : null);
        }
    }

    @Transactional
    public void completeByShowingAndKind(UUID showingId, Task.Kind kind) {
        for (Task task : repository.findByShowingIdAndKind(showingId, kind)) {
            if (task.getStatus() == Task.Status.OPEN) {
                task.setStatus(Task.Status.DONE);
                task.setCompletedAt(Instant.now());
            }
        }
    }

    public List<Task> between(Instant from, Instant to, UUID agentId) {
        return agentId == null ? repository.findByDueAtBetweenOrderByDueAtAsc(from, to)
                : repository.findByAssigneeAgentIdAndDueAtBetweenOrderByDueAtAsc(agentId, from, to);
    }

    public List<Task> overdue(UUID agentId, Instant now) {
        return repository.findByAssigneeAgentIdAndStatusAndDueAtLessThanOrderByDueAtAsc(agentId, Task.Status.OPEN, now);
    }

    public List<Task> open(UUID agentId) {
        return repository.findByAssigneeAgentIdAndStatusOrderByDueAtAsc(agentId, Task.Status.OPEN);
    }

    public List<Task> forShowing(UUID showingId) {
        return repository.findByShowingId(showingId);
    }
}

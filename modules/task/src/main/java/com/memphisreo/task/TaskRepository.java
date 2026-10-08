package com.memphisreo.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByAssigneeAgentIdAndDueAtBetweenOrderByDueAtAsc(UUID agentId, Instant from, Instant to);

    List<Task> findByDueAtBetweenOrderByDueAtAsc(Instant from, Instant to);

    List<Task> findByAssigneeAgentIdAndStatusAndDueAtLessThanOrderByDueAtAsc(UUID agentId, Task.Status status, Instant before);

    List<Task> findByAssigneeAgentIdAndStatusOrderByDueAtAsc(UUID agentId, Task.Status status);

    List<Task> findByShowingId(UUID showingId);

    List<Task> findByShowingIdAndKind(UUID showingId, Task.Kind kind);

    long countByAssigneeAgentIdAndStatusAndDueAtBetween(UUID agentId, Task.Status status, Instant from, Instant to);
}

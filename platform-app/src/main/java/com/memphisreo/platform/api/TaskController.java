package com.memphisreo.platform.api;

import com.memphisreo.platform.showing.ShowingDtos.NewTaskRequest;
import com.memphisreo.platform.showing.ShowingDtos.TaskView;
import com.memphisreo.platform.showing.ShowingWorkflowService;
import com.memphisreo.security.jwt.AuthenticatedAgent;
import com.memphisreo.task.Task;
import com.memphisreo.task.TaskService;
import com.memphisreo.task.TaskService.NewTask;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Задачі агента. Без окремого permission — кожен агент бачить і веде свої; керівник — усіх (параметр agentId). */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final ShowingWorkflowService workflow;

    public TaskController(TaskService taskService, ShowingWorkflowService workflow) {
        this.taskService = taskService;
        this.workflow = workflow;
    }

    /** Відкриті задачі агента: протерміновані + майбутні; done — за період. */
    @GetMapping
    public ResponseEntity<List<TaskView>> list(@AuthenticationPrincipal AuthenticatedAgent p,
                                               @RequestParam(required = false) UUID agentId,
                                               @RequestParam(defaultValue = "false") boolean includeDone) {
        UUID agent = agentId == null ? p.agentId() : agentId;
        List<Task> tasks = new ArrayList<>(taskService.open(agent));
        if (includeDone) {
            tasks.addAll(taskService.between(Instant.now().minusSeconds(30L * 86400), Instant.now().plusSeconds(90L * 86400), agent)
                    .stream().filter(t -> t.getStatus() != Task.Status.OPEN).toList());
        }
        return ResponseEntity.ok(workflow.taskViews(tasks));
    }

    @PostMapping
    public ResponseEntity<TaskView> create(@AuthenticationPrincipal AuthenticatedAgent p, @RequestBody NewTaskRequest r) {
        Task task = taskService.create(p.tenantId(), p.agentId(), new NewTask(Task.Kind.CUSTOM, r.title(), r.dueAt(),
                r.assigneeAgentId(), r.propertyId(), r.clientId(), null, r.note()));
        return ResponseEntity.ok(workflow.taskViews(List.of(task)).get(0));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<TaskView> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(workflow.taskViews(List.of(taskService.complete(id))).get(0));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<TaskView> reopen(@PathVariable UUID id) {
        return ResponseEntity.ok(workflow.taskViews(List.of(taskService.reopen(id))).get(0));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<TaskView> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(workflow.taskViews(List.of(taskService.cancel(id))).get(0));
    }
}

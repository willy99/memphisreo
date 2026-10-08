package com.memphisreo.activity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Запис подій. Викликається з композиційного кореня (platform-app) після
 * дій сервісів — модулі домену про таймлайн не знають (architecture.md §2).
 * Записується в транзакції виклику: подія без дії або дія без події не буває.
 */
@Service
public class ActivityRecorder {

    private final ActivityEventRepository repository;

    public ActivityRecorder(ActivityEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ActivityEvent record(UUID tenantId, ActivityEvent.SubjectType subjectType, UUID subjectId, String type,
                                Map<String, Object> payload, UUID actorAgentId, boolean ownerVisible) {
        ActivityEvent event = new ActivityEvent();
        event.setTenantId(tenantId);
        event.setSubjectType(subjectType);
        event.setSubjectId(subjectId);
        event.setType(type);
        event.setPayload(payload == null ? Map.of() : payload);
        event.setActorAgentId(actorAgentId);
        event.setOwnerVisible(ownerVisible);
        return repository.save(event);
    }

    public List<ActivityEvent> timeline(ActivityEvent.SubjectType subjectType, UUID subjectId, boolean ownerOnly) {
        return ownerOnly
                ? repository.findBySubjectTypeAndSubjectIdAndOwnerVisibleTrueOrderByCreatedAtDesc(subjectType, subjectId)
                : repository.findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(subjectType, subjectId);
    }
}

package com.memphisreo.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

    List<ActivityEvent> findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(ActivityEvent.SubjectType subjectType, UUID subjectId);

    List<ActivityEvent> findBySubjectTypeAndSubjectIdAndOwnerVisibleTrueOrderByCreatedAtDesc(
            ActivityEvent.SubjectType subjectType, UUID subjectId);
}

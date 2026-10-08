package com.memphisreo.deal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShowingRepository extends JpaRepository<Showing, UUID> {

    List<Showing> findByPropertyIdOrderByScheduledAtDesc(UUID propertyId);

    List<Showing> findByIdInOrderByScheduledAtDesc(Collection<UUID> ids);

    List<Showing> findByScheduledAtBetweenOrderByScheduledAtAsc(Instant from, Instant to);

    List<Showing> findByAgentIdAndScheduledAtBetweenOrderByScheduledAtAsc(UUID agentId, Instant from, Instant to);

    /** Активні покази агента, що перетинаються з інтервалом (для перевірки накладок). */
    List<Showing> findByAgentIdAndStatusInAndScheduledAtLessThan(UUID agentId, Collection<Showing.Status> statuses, Instant before);

    List<Showing> findByPropertyIdAndStatusInAndScheduledAtLessThan(UUID propertyId, Collection<Showing.Status> statuses, Instant before);

    /** Відбуті покази без фідбеку — для нагадувань. */
    List<Showing> findByStatusAndFeedbackAtIsNull(Showing.Status status);
}

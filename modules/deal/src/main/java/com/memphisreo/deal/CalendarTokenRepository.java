package com.memphisreo.deal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CalendarTokenRepository extends JpaRepository<CalendarToken, UUID> {

    Optional<CalendarToken> findByAgentId(UUID agentId);

    Optional<CalendarToken> findByTokenHash(String tokenHash);
}

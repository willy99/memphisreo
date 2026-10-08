package com.memphisreo.deal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShowingClientRepository extends JpaRepository<ShowingClient, UUID> {

    List<ShowingClient> findByShowingIdIn(Collection<UUID> showingIds);

    List<ShowingClient> findByClientId(UUID clientId);

    void deleteByShowingId(UUID showingId);
}

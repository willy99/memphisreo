package com.memphisreo.crm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRequirementRepository extends JpaRepository<ClientRequirement, UUID> {

    Optional<ClientRequirement> findByClientId(UUID clientId);

    List<ClientRequirement> findByActiveTrue();
}

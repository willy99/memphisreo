package com.memphisreo.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PropertyAgentRepository extends JpaRepository<PropertyAgent, UUID> {

    List<PropertyAgent> findByPropertyIdOrderByCreatedAtAsc(UUID propertyId);

    List<PropertyAgent> findByPropertyIdIn(Collection<UUID> propertyIds);

    List<PropertyAgent> findByAgentId(UUID agentId);
}

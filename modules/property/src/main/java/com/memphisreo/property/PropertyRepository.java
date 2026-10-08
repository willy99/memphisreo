package com.memphisreo.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    boolean existsByCreatedByAgentId(UUID agentId);
}

package com.memphisreo.deal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShowingForm(UUID propertyId, List<UUID> clientIds, UUID agentId, Instant scheduledAt,
                          Integer durationMinutes, String notes, Boolean ignoreAgentOverlap) {
}


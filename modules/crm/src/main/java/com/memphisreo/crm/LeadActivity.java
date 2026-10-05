package com.memphisreo.crm;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lead_activity")
@Getter
@Setter
@NoArgsConstructor
public class LeadActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(nullable = false, columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

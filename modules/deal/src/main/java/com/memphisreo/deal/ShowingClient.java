package com.memphisreo.deal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

@Entity
@Table(name = "showing_client")
@Getter
@Setter
@NoArgsConstructor
public class ShowingClient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "showing_id", nullable = false)
    private UUID showingId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
}

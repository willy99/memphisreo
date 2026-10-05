package com.memphisreo.media;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** property_id — сире посилання на Property, не JPA-зв'язок через межу модуля. */
@Entity
@Table(name = "property_media")
@Getter
@Setter
@NoArgsConstructor
public class PropertyMedia {

    public enum Type { PHOTO, VIDEO, TOUR_3D }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    /** Object storage (S3-сумісне), не локальний диск — docs/architecture.md §4. */
    @Column(nullable = false)
    private String url;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

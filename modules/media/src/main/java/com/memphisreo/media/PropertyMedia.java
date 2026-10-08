package com.memphisreo.media;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Медіа об'єкта. property_id — сире посилання через межу модуля.
 * Фото/планування зберігаються лише як перекодовані JPEG (large + thumb):
 * EXIF і GPS з оригіналу не потрапляють у сховище. Відео — оригінал файлу.
 */
@Entity
@Table(name = "property_media")
@Getter
@Setter
@NoArgsConstructor
public class PropertyMedia {

    public enum Kind { PHOTO, FLOORPLAN, VIDEO, VIDEO_LINK }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_cover", nullable = false)
    private boolean cover;

    private String caption;

    @Column(name = "large_key")
    private String largeKey;

    @Column(name = "thumb_key")
    private String thumbKey;

    @Column(name = "original_key")
    private String originalKey;

    @Column(name = "external_url")
    private String externalUrl;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    private Integer width;
    private Integer height;

    @Column(name = "uploaded_by_agent_id", nullable = false)
    private UUID uploadedByAgentId;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

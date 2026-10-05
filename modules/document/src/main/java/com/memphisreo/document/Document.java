package com.memphisreo.document;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * property_id/deal_id — сирі посилання через межу модуля (deal_id ще не
 * існує — Showing/Offer/Deal не реалізовані, поле вже закладене).
 * docs/domain-model.md §6.
 */
@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    /** Конкретний перелік з українського законодавства — docs/domain-model.md §6. */
    public enum Type {
        TECHNICAL_PASSPORT, TITLE_DEED, REGISTRY_EXTRACT, APPRAISAL_REPORT,
        RESIDENTS_CERTIFICATE, FLOOR_PLAN, SALE_CONTRACT, INSPECTION_ACT, OTHER
    }

    /** Лістер + юрист агенції — через DOCUMENT_VIEW_CONFIDENTIAL, не новий RBAC-механізм. */
    public enum Visibility { AGENCY_INTERNAL, LISTING_AGENT_AND_LAWYER, PUBLIC }

    public enum ParsingStatus { NOT_REQUESTED, PENDING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    /** Nullable — заповнено лише для документів конкретної угоди (напр. сам договір). */
    @Column(name = "deal_id")
    private UUID dealId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** Звіт про оцінку діє 6 міс, витяг має бути свіжим тощо — нагадування поки не реалізовано. */
    @Column(name = "valid_until")
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility = Visibility.AGENCY_INTERNAL;

    /**
     * Закладено під майбутній парсинг (витягувати деталі об'єкта з
     * техпаспорта при завантаженні) — не реалізовано, тільки поле.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "parsing_status", nullable = false)
    private ParsingStatus parsingStatus = ParsingStatus.NOT_REQUESTED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_data", columnDefinition = "jsonb")
    private String extractedDataJson;

    @Column(name = "uploaded_by_agent_id", nullable = false)
    private UUID uploadedByAgentId;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

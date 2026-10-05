package com.memphisreo.security.rbac;

/**
 * Фіксований каталог дозволів — код, не таблиця в БД (docs/security.md §3).
 * Кожне значення відповідає конкретній {@code @PreAuthorize} перевірці в коді;
 * новий permission без відповідного коду не має сенсу.
 */
public enum Permission {

    PROPERTY_VIEW,
    PROPERTY_CREATE,
    PROPERTY_EDIT,
    PROPERTY_DELETE,

    LISTING_VIEW,
    LISTING_PUBLISH,
    LISTING_EDIT,
    LISTING_CLOSE,

    INQUIRY_VIEW,
    INQUIRY_MANAGE,

    LEAD_VIEW,
    LEAD_MANAGE,
    CLIENT_VIEW,
    CLIENT_MANAGE,

    AGENT_INVITE,
    AGENT_MANAGE,
    ROLE_MANAGE,

    TENANT_SETTINGS_MANAGE,

    DOCUMENT_VIEW,
    DOCUMENT_MANAGE,
    /** Правовстановлюючі документи — лістер + власники цього permission-у. docs/domain-model.md §6. */
    DOCUMENT_VIEW_CONFIDENTIAL
}

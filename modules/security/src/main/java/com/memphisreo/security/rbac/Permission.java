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

    AGENT_INVITE,
    AGENT_MANAGE,
    ROLE_MANAGE,

    TENANT_SETTINGS_MANAGE
}

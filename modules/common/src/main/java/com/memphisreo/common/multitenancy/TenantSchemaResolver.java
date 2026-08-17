package com.memphisreo.common.multitenancy;

import java.util.UUID;

/**
 * Публічний контракт "tenant_id → schema_name", яким користуються інші
 * модулі (напр. security при логіні) без прямої залежності на модуль
 * tenant — реалізація там, контракт тут. docs/architecture.md §2.
 */
public interface TenantSchemaResolver {

    String resolveSchema(UUID tenantId);
}

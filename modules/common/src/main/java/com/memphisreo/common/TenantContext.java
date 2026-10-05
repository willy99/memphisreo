package com.memphisreo.common;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Поточний tenant запиту. Заповнюється JWT security-фільтром (docs/security.md §7)
 * або явно через {@link #callAs} у публічних флоу, де tenant відомий без JWT
 * (логін, реєстрація, публічна заявка за slug).
 *
 * Споживачі: Hibernate {@code @TenantId}-фільтр і {@code SET LOCAL app.tenant_id}
 * для RLS на початку кожної транзакції — docs/adr/001-shared-schema-rls-cells.md.
 * tenant_id тут МАЄ походити з підписаного JWT або з серверного lookup-у,
 * ніколи напряму з параметра запиту — docs/security.md §8.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Виконує дію від імені tenant-а, відновлюючи попередній контекст після. */
    public static <T> T callAs(UUID tenantId, Supplier<T> action) {
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(UUID tenantId, Runnable action) {
        callAs(tenantId, () -> {
            action.run();
            return null;
        });
    }
}

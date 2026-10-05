package com.memphisreo.common.multitenancy;

/**
 * Спроба завантажити запис іншої агенції. Не мала б статись ніколи: означає,
 * що RLS (рубіж 2 з ADR-001) не спрацював. Клієнту — 404 (не розкриваємо
 * існування запису), нам — ERROR у лог.
 */
public class TenantIsolationViolationException extends RuntimeException {

    public TenantIsolationViolationException(String message) {
        super(message);
    }
}

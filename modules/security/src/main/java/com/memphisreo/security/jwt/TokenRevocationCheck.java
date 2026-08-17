package com.memphisreo.security.jwt;

import java.util.UUID;

/**
 * Миттєве відкликання токена (заблокований/скомпрометований акаунт) —
 * порівняння token_version з токена проти поточного в БД. Дешева
 * індексована перевірка, не повний blocklist. docs/security.md §4.
 */
public interface TokenRevocationCheck {

    boolean isStillValid(UUID accountIdentityId, int tokenVersionInToken);
}

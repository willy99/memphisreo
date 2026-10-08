import { useCallback, useEffect, useState } from "react";
import { onUnauthorized, type Realm } from "../api/client";

/**
 * Токен у sessionStorage: переживає перезавантаження сторінки, але не
 * закриття вкладки й не ділиться між вкладками. Перехідний варіант до
 * httpOnly refresh-cookie (docs/security.md §4).
 */
export function useSessionToken(realm: Realm) {
  const key = `memphisreo-token-${realm}`;
  const [token, setTokenState] = useState<string | null>(() => read(key));

  const setToken = useCallback(
    (value: string | null) => {
      try {
        if (value) sessionStorage.setItem(key, value);
        else sessionStorage.removeItem(key);
      } catch {
        // сховище недоступне (приватний режим тощо) — токен лишається лише в пам'яті
      }
      setTokenState(value);
    },
    [key],
  );

  useEffect(() => onUnauthorized(realm, () => setToken(null)), [realm, setToken]);

  return [token, setToken] as const;
}

function read(key: string): string | null {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

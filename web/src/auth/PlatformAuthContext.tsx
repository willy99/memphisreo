import { createContext, useContext, type ReactNode } from "react";
import { api } from "../api/client";
import type { LoginResult } from "../api/types";
import { useSessionToken } from "./sessionToken";

interface PlatformAuthState {
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

/**
 * Сесія супер-адміна платформи — окремий realm з окремим токеном
 * (інший ключ підпису на бекенді, docs/security.md §6).
 */
const PlatformAuthContext = createContext<PlatformAuthState | null>(null);

export function PlatformAuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useSessionToken("platform");

  async function login(email: string, password: string) {
    const result = await api.post<LoginResult>("/platform-admin/auth/login", { email, password });
    setToken(result.accessToken);
  }

  function logout() {
    setToken(null);
  }

  return (
    <PlatformAuthContext.Provider value={{ token, login, logout }}>{children}</PlatformAuthContext.Provider>
  );
}

export function usePlatformAuth(): PlatformAuthState {
  const context = useContext(PlatformAuthContext);
  if (!context) {
    throw new Error("usePlatformAuth має використовуватись всередині PlatformAuthProvider");
  }
  return context;
}

import { createContext, useContext, type ReactNode } from "react";
import { api } from "../api/client";
import type { LoginResult } from "../api/types";
import { useSessionToken } from "./sessionToken";

interface AuthState {
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

/** Сесія агента агенції (tenant realm). */
const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useSessionToken("tenant");

  async function login(email: string, password: string) {
    const result = await api.post<LoginResult>("/api/auth/login", { email, password });
    setToken(result.accessToken);
  }

  function logout() {
    setToken(null);
  }

  return <AuthContext.Provider value={{ token, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth має використовуватись всередині AuthProvider");
  }
  return context;
}

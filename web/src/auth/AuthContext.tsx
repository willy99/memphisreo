import { createContext, useContext, useState, type ReactNode } from "react";
import { api } from "../api/client";
import type { LoginResult } from "../api/types";

interface AuthState {
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

// Токен свідомо тримається в пам'яті (React state), не localStorage —
// зменшує ризик крадіжки через XSS (docs/security.md §4). Наслідок:
// перезавантаження сторінки скидає сесію — прийнятний trade-off для
// цього мінімального старту, продакшн-версія піде на httpOnly refresh cookie.
const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);

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

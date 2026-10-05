import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, ApiError, setUnauthorizedHandler } from "./api";
import { session } from "./session";
import type { User } from "./types";

interface AuthState {
  user: User | null;
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(() => session.user());
  const [token, setToken] = useState<string | null>(() => session.token());

  const logout = useCallback(() => {
    session.clear();
    setUser(null);
    setToken(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => setUnauthorizedHandler(logout), [logout]);

  const login = useCallback(async (email: string, password: string) => {
    const result = await api.login(email.trim(), password);
    if (result.user.role !== "DISPATCHER") {
      throw new ApiError(403, "This is a courier account. Couriers sign in with the ProofDrop Android app.");
    }
    session.save(result.accessToken, result.user);
    setUser(result.user);
    setToken(result.accessToken);
  }, []);

  const value = useMemo(() => ({ user, token, login, logout }), [user, token, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}

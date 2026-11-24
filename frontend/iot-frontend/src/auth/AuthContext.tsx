import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import axios from 'axios';
import type { UserDTO } from '../types/api';
import type { RegisterRequest } from '../types/api';
import { login as loginRequest, register as registerRequest } from './authApi';
import { cloudApi, isOffline } from '../api/client';
import type { ApiClient } from '../api/client';
import { edgeApi } from '@target';
import { decodeJwtClaims, isAccessTokenExpired } from './storage';

interface AuthContextValue {
  user: UserDTO | null;
  hasCloud: boolean;
  hasEdge: boolean;
  hasDeviceSession: boolean;
  canManageDevices: boolean;
  isAuthenticated: boolean;
  isInitializing: boolean;
  login: (identifier: string, password: string) => Promise<void>;
  register: (payload: RegisterRequest) => Promise<void>;
  loginEdge: (identifier: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

async function restore(api: ApiClient): Promise<UserDTO | null> {
  const session = api.session.load();
  if (!session) {
    api.session.clear();
    return null;
  }
  if (!isAccessTokenExpired(session.accessToken)) {
    try {
      await axios.post(`${api.http.defaults.baseURL}/auth/refresh`, {});
    } catch (error) {
      if (isOffline(error)) return null;
    }
    return session.user;
  }
  try {
    await api.refresh();
    return api.session.load()?.user ?? null;
  } catch (error) {
    if (!isOffline(error)) api.session.clear();
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDTO | null>(null);
  const [hasEdge, setHasEdge] = useState(false);
  const [isInitializing, setIsInitializing] = useState(true);

  useEffect(() => {
    cloudApi.onSessionLost = () => setUser(null);
    if (edgeApi) edgeApi.onSessionLost = () => setHasEdge(false);
  }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.all([restore(cloudApi), edgeApi ? restore(edgeApi) : null]).then(
      ([cloudUser, edgeUser]) => {
        if (cancelled) return;
        setUser(cloudUser);
        setHasEdge(edgeUser !== null);
        setIsInitializing(false);
      },
    );
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (identifier: string, password: string): Promise<void> => {
    const apis = edgeApi ? [cloudApi, edgeApi] : [cloudApi];
    const results = await Promise.allSettled(
      apis.map((api) => loginRequest(api.http, { identifier, password })),
    );
    results.forEach((result, i) => {
      if (result.status === 'fulfilled') {
        const auth = result.value;
        apis[i].session.save({
          accessToken: auth.accessToken,
          refreshToken: auth.refreshToken,
          user: auth.user,
        });
      }
    });
    const [cloudResult, edgeResult] = results;
    if (cloudResult.status === 'fulfilled') setUser(cloudResult.value.user);
    if (edgeResult?.status === 'fulfilled') setHasEdge(true);

    const failures = results.flatMap((r) => (r.status === 'rejected' ? [r.reason] : []));
    if (failures.length === results.length) {
      throw failures.find((e) => axios.isAxiosError(e) && e.response) ?? failures[0];
    }
  }, []);

  const register = useCallback(async (payload: RegisterRequest): Promise<void> => {
    const auth = await registerRequest(payload);
    cloudApi.session.save({
      accessToken: auth.accessToken,
      refreshToken: auth.refreshToken,
      user: auth.user,
    });
    setUser(auth.user);
  }, []);

  const loginEdge = useCallback(async (identifier: string, password: string): Promise<void> => {
    if (!edgeApi) throw new Error('loginEdge solo existe en el Edge-front');
    const auth = await loginRequest(edgeApi.http, { identifier, password });
    edgeApi.session.save({
      accessToken: auth.accessToken,
      refreshToken: auth.refreshToken,
      user: auth.user,
    });
    setHasEdge(true);
  }, []);

  const logout = useCallback((): void => {
    cloudApi.session.clear();
    edgeApi?.session.clear();
    setUser(null);
    setHasEdge(false);
  }, []);

  const value = useMemo<AuthContextValue>(() => {
    const hasCloud = user !== null;
    const edgeToken = hasEdge ? edgeApi?.session.getAccessToken() : null;
    const edgeRole = edgeToken ? decodeJwtClaims(edgeToken)?.role : null;
    return {
      user,
      hasCloud,
      hasEdge,
      hasDeviceSession: edgeApi ? hasEdge : hasCloud,
      canManageDevices: edgeRole === 'OWNER' || edgeRole === 'ADMIN',
      isAuthenticated: hasCloud || hasEdge,
      isInitializing,
      login,
      register,
      loginEdge,
      logout,
    };
  }, [user, hasEdge, isInitializing, login, register, loginEdge, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  }
  return ctx;
}

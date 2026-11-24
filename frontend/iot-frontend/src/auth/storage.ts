import type { UserDTO } from '../types/api';

export interface StoredSession {
  accessToken: string;
  refreshToken: string;
  user: UserDTO;
}

export function decodeJwtClaims(token: string): Record<string, unknown> | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    return JSON.parse(atob(padded)) as Record<string, unknown>;
  } catch {
    return null;
  }
}

export function isAccessTokenExpired(token: string): boolean {
  const exp = decodeJwtClaims(token)?.exp;
  return typeof exp !== 'number' || exp * 1000 <= Date.now();
}

export interface SessionStore {
  getAccessToken: () => string | null;
  getRefreshToken: () => string | null;
  load: () => StoredSession | null;
  save: (session: StoredSession) => void;
  clear: () => void;
}

export function createSessionStore(prefix: string): SessionStore {
  const accessKey = `iot.${prefix}.accessToken`;
  const refreshKey = `iot.${prefix}.refreshToken`;
  const userKey = `iot.${prefix}.user`;
  return {
    getAccessToken: () => localStorage.getItem(accessKey),
    getRefreshToken: () => localStorage.getItem(refreshKey),
    load: () => {
      const accessToken = localStorage.getItem(accessKey);
      const refreshToken = localStorage.getItem(refreshKey);
      const userRaw = localStorage.getItem(userKey);
      if (!accessToken || !refreshToken || !userRaw) {
        return null;
      }
      try {
        return { accessToken, refreshToken, user: JSON.parse(userRaw) as UserDTO };
      } catch {
        return null;
      }
    },
    save: (session) => {
      localStorage.setItem(accessKey, session.accessToken);
      localStorage.setItem(refreshKey, session.refreshToken);
      localStorage.setItem(userKey, JSON.stringify(session.user));
    },
    clear: () => {
      localStorage.removeItem(accessKey);
      localStorage.removeItem(refreshKey);
      localStorage.removeItem(userKey);
    },
  };
}

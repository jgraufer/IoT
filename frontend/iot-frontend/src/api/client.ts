import axios from 'axios';
import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import { CLOUD_API_URL } from './config';
import type { ApiResponse, AuthResponse } from '../types/api';
import { createSessionStore } from '../auth/storage';
import type { SessionStore } from '../auth/storage';

export interface ApiClient {
  http: AxiosInstance;
  session: SessionStore;
  refresh: () => Promise<string>;
  onSessionLost: () => void;
}

interface RetryableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

export function isOffline(error: unknown): boolean {
  return axios.isAxiosError(error) && !error.response;
}

export function createApiClient(baseURL: string, storagePrefix: string): ApiClient {
  const http = axios.create({ baseURL });
  const session = createSessionStore(storagePrefix);

  let refreshPromise: Promise<string> | null = null;

  async function doRefresh(): Promise<string> {
    const refreshToken = session.getRefreshToken();
    if (!refreshToken) {
      throw new Error('No refresh token');
    }
    const { data } = await axios.post<ApiResponse<AuthResponse>>(`${baseURL}/auth/refresh`, {
      refreshToken,
    });
    const auth = data.data;
    session.save({ accessToken: auth.accessToken, refreshToken: auth.refreshToken, user: auth.user });
    return auth.accessToken;
  }

  const client: ApiClient = {
    http,
    session,
    refresh: () => {
      if (!refreshPromise) {
        refreshPromise = doRefresh().finally(() => {
          refreshPromise = null;
        });
      }
      return refreshPromise;
    },
    onSessionLost: () => {},
  };

  http.interceptors.request.use((config) => {
    const token = session.getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  http.interceptors.response.use(
    (response) => response,
    async (error: unknown) => {
      if (!axios.isAxiosError(error)) {
        return Promise.reject(error);
      }
      const original = error.config as RetryableConfig | undefined;
      const isAuthCall = (original?.url ?? '').includes('/auth/');

      if (error.response?.status === 401 && original && !isAuthCall && !original._retry) {
        original._retry = true;
        try {
          await client.refresh();
          return http(original);
        } catch (refreshError) {
          if (!isOffline(refreshError)) {
            session.clear();
            client.onSessionLost();
          }
          return Promise.reject(error);
        }
      }

      return Promise.reject(error);
    },
  );

  return client;
}

export const cloudApi = createApiClient(CLOUD_API_URL, 'cloud');

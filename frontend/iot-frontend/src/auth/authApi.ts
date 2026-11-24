import type { AxiosInstance } from 'axios';
import { cloudApi } from '../api/client';
import type { ApiResponse, AuthResponse, LoginRequest, RegisterRequest } from '../types/api';

export async function login(http: AxiosInstance, payload: LoginRequest): Promise<AuthResponse> {
  const { data } = await http.post<ApiResponse<AuthResponse>>('/auth/login', payload);
  return data.data;
}

export async function register(payload: RegisterRequest): Promise<AuthResponse> {
  const { data } = await cloudApi.http.post<ApiResponse<AuthResponse>>('/auth/register', payload);
  return data.data;
}

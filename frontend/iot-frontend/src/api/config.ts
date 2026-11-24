export const CLOUD_API_URL =
  (import.meta.env.VITE_CLOUD_API_URL as string | undefined) ?? 'http://localhost:8081/api';

export const EDGE_API_URL =
  (import.meta.env.VITE_EDGE_API_URL as string | undefined) ?? 'http://localhost:8082/api';

import axios from 'axios';

export interface ApiError {
  fieldErrors: Record<string, string>;
  message: string | null;
}

export function parseApiError(err: unknown, fallback: string): ApiError {
  if (axios.isAxiosError(err) && err.response) {
    const body = err.response.data as { message?: string; data?: unknown } | undefined;
    if (err.response.status === 400 && body?.data && typeof body.data === 'object') {
      return { fieldErrors: body.data as Record<string, string>, message: null };
    }
    return { fieldErrors: {}, message: body?.message ?? fallback };
  }
  return { fieldErrors: {}, message: fallback };
}

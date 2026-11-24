import { cloudApi } from '../api/client';
import { edgeApi } from '@target';
import type { ApiResponse, EdgeInfo, PairingCode } from '../types/api';

function edgeHttp() {
  if (!edgeApi) throw new Error('El pairing solo existe en el Edge-front');
  return edgeApi.http;
}

export async function getEdgeInfo(): Promise<EdgeInfo> {
  const { data } = await edgeHttp().get<ApiResponse<EdgeInfo>>('/edge/info');
  return data.data;
}

export async function getPairingCode(): Promise<PairingCode> {
  const { data } = await edgeHttp().get<ApiResponse<PairingCode>>('/edge/pairing-code');
  return data.data;
}

export async function pairEdge(code: string): Promise<void> {
  await cloudApi.http.post('/edges/pair', { code });
}

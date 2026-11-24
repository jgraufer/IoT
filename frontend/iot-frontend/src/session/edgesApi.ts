import { cloudApi } from '../api/client';
import type { ApiResponse, EdgeDetail, EdgeSummary } from '../types/api';

export async function listMyEdges(): Promise<EdgeSummary[]> {
  const { data } = await cloudApi.http.get<ApiResponse<EdgeSummary[]>>('/users/me/edges');
  return data.data;
}

export async function getEdgeDetail(edgeId: string): Promise<EdgeDetail> {
  const { data } = await cloudApi.http.get<ApiResponse<EdgeDetail>>(`/edges/${edgeId}`);
  return data.data;
}

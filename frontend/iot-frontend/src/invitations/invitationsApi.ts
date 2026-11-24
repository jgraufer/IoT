import { cloudApi } from '../api/client';
import type { ApiResponse, Invitation } from '../types/api';

export async function listMyInvitations(): Promise<Invitation[]> {
  const { data } = await cloudApi.http.get<ApiResponse<Invitation[]>>('/users/me/invitations');
  return data.data;
}

export async function acceptInvitation(invitationId: string): Promise<void> {
  await cloudApi.http.post(`/users/me/invitations/${invitationId}/accept`);
}

export async function rejectInvitation(invitationId: string): Promise<void> {
  await cloudApi.http.post(`/users/me/invitations/${invitationId}/reject`);
}

export async function listSentInvitations(edgeId: string): Promise<Invitation[]> {
  const { data } = await cloudApi.http.get<ApiResponse<Invitation[]>>(`/edges/${edgeId}/invitations`);
  return data.data;
}

export async function inviteToEdge(edgeId: string, email: string): Promise<Invitation> {
  const { data } = await cloudApi.http.post<ApiResponse<Invitation>>(
    `/edges/${edgeId}/invitations`,
    { email },
  );
  return data.data;
}

export async function cancelInvitation(edgeId: string, invitationId: string): Promise<void> {
  await cloudApi.http.delete(`/edges/${edgeId}/invitations/${invitationId}`);
}

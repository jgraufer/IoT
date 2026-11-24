import { deviceClient, deviceRoutes } from '@target';
import type { ApiResponse, DeviceState, IssueCommandRequest } from '../types/api';

export async function getDeviceState(edgeId: string, deviceId: string): Promise<DeviceState> {
  const { data } = await deviceClient.get<ApiResponse<DeviceState>>(
    deviceRoutes.state(edgeId, deviceId),
  );
  return data.data;
}

export async function sendCommand(
  edgeId: string,
  deviceId: string,
  body: IssueCommandRequest,
): Promise<void> {
  await deviceClient.post(deviceRoutes.commands(edgeId, deviceId), body);
}

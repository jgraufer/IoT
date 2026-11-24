import { deviceClient, deviceRoutes } from '@target';
import type {
  ApiResponse,
  CapabilityType,
  CreateDeviceRequest,
  Device,
  TelemetryAggregatePoint,
} from '../types/api';

export async function listDevices(edgeId: string): Promise<Device[]> {
  const { data } = await deviceClient.get<ApiResponse<Device[]>>(deviceRoutes.devices(edgeId));
  return data.data;
}

export async function getDevice(edgeId: string, deviceId: string): Promise<Device> {
  const { data } = await deviceClient.get<ApiResponse<Device>>(deviceRoutes.device(edgeId, deviceId));
  return data.data;
}

export async function createDevice(edgeId: string, body: CreateDeviceRequest): Promise<Device> {
  const { data } = await deviceClient.post<ApiResponse<Device>>(deviceRoutes.devices(edgeId), body);
  return data.data;
}

export async function renameDevice(edgeId: string, deviceId: string, name: string): Promise<Device> {
  const { data } = await deviceClient.patch<ApiResponse<Device>>(deviceRoutes.device(edgeId, deviceId), {
    name,
  });
  return data.data;
}

export async function deleteDevice(edgeId: string, deviceId: string): Promise<void> {
  await deviceClient.delete(deviceRoutes.device(edgeId, deviceId));
}

export interface TelemetryAggregateParams {
  capability: CapabilityType;
  since?: string;
  until?: string;
  bucketMinutes?: number;
}

export async function getTelemetryAggregate(
  edgeId: string,
  deviceId: string,
  params: TelemetryAggregateParams,
): Promise<TelemetryAggregatePoint[]> {
  const { data } = await deviceClient.get<ApiResponse<TelemetryAggregatePoint[]>>(
    deviceRoutes.aggregate(edgeId, deviceId),
    { params },
  );
  return data.data;
}

export async function getTelemetryAverage(
  edgeId: string,
  params: TelemetryAggregateParams,
): Promise<TelemetryAggregatePoint[]> {
  const { data } = await deviceClient.get<ApiResponse<TelemetryAggregatePoint[]>>(
    deviceRoutes.average(edgeId),
    { params },
  );
  return data.data;
}

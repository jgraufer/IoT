export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

export type EdgeRole = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface UserDTO {
  id: string;
  username: string | null;
  email: string;
  name: string;
  isActive: boolean;
  createdAt: string | null;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  user: UserDTO;
  isEdgeToken: boolean;
}

export interface LoginRequest {
  identifier: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  username: string;
  password: string;
}

export interface EdgeSummary {
  edgeId: string;
  name: string;
  role: EdgeRole;
}

export interface EdgeInfo {
  edgeId: string;
  paired: boolean;
}

export interface PairingCode {
  pairingCode: string;
  expiresAt: string | null;
}

export interface EdgeMember {
  userId: string;
  name: string | null;
  username: string | null;
  email: string;
  role: EdgeRole;
  grantedAt: string;
}

export interface EdgeDetail {
  edgeId: string;
  name: string;
  role: EdgeRole;
  pairedAt: string | null;
  lastSeen: string | null;
  members: EdgeMember[];
}

export interface Invitation {
  id: string;
  edgeId: string;
  edgeName: string | null;
  invitedUserId: string;
  invitedUsername: string | null;
  invitedEmail: string | null;
  invitedName: string | null;
  invitedBy: string;
  createdAt: string;
  expiresAt: string | null;
}

export type DeviceType = 'LIGHT' | 'THERMOSTAT' | 'SENSOR' | 'OUTLET' | 'OTHER';

export type CapabilityType =
  | 'POWER_SWITCH'
  | 'BRIGHTNESS'
  | 'TEMPERATURE'
  | 'HUMIDITY'
  | 'MOTION_DETECTED'
  | 'BATTERY_LEVEL';

export interface Device {
  id: string;
  edgeId: string;
  name: string;
  type: DeviceType;
  capabilities: CapabilityType[];
  createdAt: string;
  updatedAt: string;
  lastSyncedAt?: string | null;
}

export interface CreateDeviceRequest {
  name: string;
  type: DeviceType;
  capabilities: CapabilityType[];
}

export interface CapabilityState {
  capability: CapabilityType;
  value: unknown;
  timestamp: string;
}

export interface DeviceState {
  deviceId: string;
  states: CapabilityState[];
}

export interface IssueCommandRequest {
  capability: CapabilityType;
  value: unknown;
}

export interface TelemetryAggregatePoint {
  timestamp: string;
  average: number;
  count: number;
}

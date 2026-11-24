import type { CapabilityState, CapabilityType, DeviceState, DeviceType } from '../types/api';

export const CAPABILITY_META: Record<
  CapabilityType,
  { kind: 'ACTIVE' | 'PASSIVE'; dataType: 'BOOLEAN' | 'INTEGER' | 'FLOAT'; unit?: string; min?: number; max?: number }
> = {
  POWER_SWITCH: { kind: 'ACTIVE', dataType: 'BOOLEAN' },
  BRIGHTNESS: { kind: 'ACTIVE', dataType: 'INTEGER', unit: '%', min: 0, max: 100 },
  TEMPERATURE: { kind: 'PASSIVE', dataType: 'FLOAT', unit: '°C' },
  HUMIDITY: { kind: 'PASSIVE', dataType: 'FLOAT', unit: '%', min: 0, max: 100 },
  MOTION_DETECTED: { kind: 'PASSIVE', dataType: 'BOOLEAN' },
  BATTERY_LEVEL: { kind: 'PASSIVE', dataType: 'INTEGER', unit: '%', min: 0, max: 100 },
};

export function isNumericCapability(cap: CapabilityType): boolean {
  return CAPABILITY_META[cap].dataType !== 'BOOLEAN';
}

export const CAPABILITY_LABEL: Record<CapabilityType, string> = {
  POWER_SWITCH: 'Encendido',
  BRIGHTNESS: 'Brillo',
  TEMPERATURE: 'Temperatura',
  HUMIDITY: 'Humedad',
  MOTION_DETECTED: 'Movimiento',
  BATTERY_LEVEL: 'Batería',
};

export const DEVICE_TYPE_LABEL: Record<DeviceType, string> = {
  LIGHT: 'Luz',
  THERMOSTAT: 'Termostato',
  SENSOR: 'Sensor',
  OUTLET: 'Enchufe',
  OTHER: 'Otro',
};

const READING_PRIORITY: CapabilityType[] = [
  'TEMPERATURE',
  'HUMIDITY',
  'MOTION_DETECTED',
  'BATTERY_LEVEL',
];

export function pickReadingCapability(caps: CapabilityType[]): CapabilityType | null {
  for (const cap of READING_PRIORITY) {
    if (caps.includes(cap)) return cap;
  }
  return null;
}

export function findCapabilityState(
  state: DeviceState | undefined,
  cap: CapabilityType,
): CapabilityState | undefined {
  return state?.states.find((s) => s.capability === cap);
}

export function readSwitchValue(state: DeviceState | undefined): boolean {
  return Boolean(findCapabilityState(state, 'POWER_SWITCH')?.value);
}

export function formatReading(cap: CapabilityType, value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (cap === 'MOTION_DETECTED') return value ? 'Detectado' : 'Sin movimiento';
  const unit = CAPABILITY_META[cap].unit;
  const text = String(value);
  return unit ? `${text} ${unit}` : text;
}

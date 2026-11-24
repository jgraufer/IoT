import { useEffect, useState } from "react";
import axios from "axios";
import type { Device, DeviceState } from "../types/api";
import { listDevices } from "../devices/devicesApi";
import { getDeviceState, sendCommand } from "./dashboardApi";
import { readSwitchValue } from "./deviceDisplay";

const POLL_INTERVAL_MS = 15_000;
const PENDING_TIMEOUT_MS = 90_000;

function omit<T>(obj: Record<string, T>, key: string): Record<string, T> {
  const rest: Record<string, T> = {};
  for (const k of Object.keys(obj)) {
    if (k !== key) rest[k] = obj[k];
  }
  return rest;
}

export interface DeviceStatesController {
  devices: Device[];
  states: Record<string, DeviceState>;
  loading: boolean;
  error: string | null;
  commandError: string | null;
  clearCommandError: () => void;
  switchValue: (deviceId: string) => boolean;
  switchBusy: (deviceId: string) => boolean;
  toggle: (device: Device, nextOn: boolean) => void;
  reload: () => void;
}

export function useDeviceStates(activeEdgeId: string | null): DeviceStatesController {
  const [devices, setDevices] = useState<Device[]>([]);
  const [states, setStates] = useState<Record<string, DeviceState>>({});
  const [pending, setPending] = useState<
    Record<string, { value: boolean; sentAt: number }>
  >({});
  const [sending, setSending] = useState<Record<string, boolean>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [commandError, setCommandError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (activeEdgeId === null) {
      setDevices([]);
      setStates({});
      setPending({});
      setSending({});
      return;
    }
    const edgeId = activeEdgeId;
    let cancelled = false;
    let intervalId: number | undefined;

    async function loadStates(list: Device[]) {
      const results = await Promise.allSettled(
        list.map((device) => getDeviceState(edgeId, device.id)),
      );
      if (cancelled) return;

      const next: Record<string, DeviceState> = {};
      results.forEach((result, i) => {
        if (result.status === "fulfilled") next[list[i].id] = result.value;
      });
      setStates(next);

      setPending((prev) => {
        const now = Date.now();
        const kept: typeof prev = {};
        for (const [id, p] of Object.entries(prev)) {
          const converged = readSwitchValue(next[id]) === p.value;
          const expired = now - p.sentAt > PENDING_TIMEOUT_MS;
          if (!converged && !expired) kept[id] = p;
        }
        return kept;
      });
    }

    setLoading(true);
    setError(null);
    listDevices(edgeId)
      .then((list) => {
        if (cancelled) return;
        setDevices(list);
        void loadStates(list);
        intervalId = window.setInterval(
          () => void loadStates(list),
          POLL_INTERVAL_MS,
        );
      })
      .catch((err) => {
        if (cancelled) return;
        const msg = axios.isAxiosError(err)
          ? (err.response?.data as { message?: string } | undefined)?.message
          : null;
        setError(msg ?? "No se pudieron cargar los dispositivos.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
      if (intervalId !== undefined) window.clearInterval(intervalId);
    };
  }, [activeEdgeId, reloadKey]);

  function toggle(device: Device, nextOn: boolean) {
    if (activeEdgeId === null) return;
    const edgeId = activeEdgeId;

    setPending((p) => ({
      ...p,
      [device.id]: { value: nextOn, sentAt: Date.now() },
    }));
    setSending((s) => ({ ...s, [device.id]: true }));

    sendCommand(edgeId, device.id, { capability: "POWER_SWITCH", value: nextOn })
      .catch(() => {
        setPending((p) => omit(p, device.id));
        setCommandError(`No se pudo enviar el comando a "${device.name}".`);
      })
      .finally(() => {
        setSending((s) => omit(s, device.id));
      });
  }

  const switchValue = (deviceId: string): boolean => {
    const p = pending[deviceId];
    return p ? p.value : readSwitchValue(states[deviceId]);
  };

  return {
    devices,
    states,
    loading,
    error,
    commandError,
    clearCommandError: () => setCommandError(null),
    switchValue,
    switchBusy: (deviceId: string) => sending[deviceId] === true,
    toggle,
    reload: () => setReloadKey((k) => k + 1),
  };
}

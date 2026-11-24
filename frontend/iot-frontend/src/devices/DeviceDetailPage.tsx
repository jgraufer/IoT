import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  IconButton,
  Slider,
  Snackbar,
  Stack,
  Switch,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import EditIcon from '@mui/icons-material/EditOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';
import type { CapabilityType, Device, DeviceState, TelemetryAggregatePoint } from '../types/api';
import { useEdges } from '../session/EdgeProvider';
import { getDevice, getTelemetryAggregate } from './devicesApi';
import { getDeviceState, sendCommand } from '../dashboard/dashboardApi';
import {
  CAPABILITY_LABEL,
  CAPABILITY_META,
  DEVICE_TYPE_LABEL,
  findCapabilityState,
  formatReading,
  isNumericCapability,
  pickReadingCapability,
  readSwitchValue,
} from '../dashboard/deviceDisplay';
import { TYPE_ICON } from '../shared/deviceIcons';
import { TelemetryChart } from './TelemetryChart';
import { DeleteDeviceDialog, EditDeviceNameDialog } from './DeviceManageDialogs';
import { useAuth } from '../auth/AuthContext';
import { CopyField } from '../shared/CopyField';
import { TELEMETRY_BUCKET_MINUTES, TELEMETRY_WINDOW_HOURS, TELEMETRY_WINDOW_LABEL } from '../shared/telemetryWindow';
import { edgeApi } from '@target';

const POLL_INTERVAL_MS = 15_000;
const PENDING_TIMEOUT_MS = 90_000;

type Pending = { value: unknown; sentAt: number };

function omit<T>(obj: Record<string, T>, key: string): Record<string, T> {
  const rest: Record<string, T> = {};
  for (const k of Object.keys(obj)) {
    if (k !== key) rest[k] = obj[k];
  }
  return rest;
}

function extractError(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const msg = (err.response?.data as { message?: string } | undefined)?.message;
    if (msg) return msg;
  }
  return fallback;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('es-ES');
}

export function DeviceDetailPage() {
  const { id: deviceId } = useParams<{ id: string }>();
  const { activeEdgeId } = useEdges();
  const navigate = useNavigate();
  const { canManageDevices } = useAuth();
  const [dialog, setDialog] = useState<'edit' | 'delete' | null>(null);

  const [device, setDevice] = useState<Device | null>(null);
  const [state, setState] = useState<DeviceState | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [points, setPoints] = useState<TelemetryAggregatePoint[]>([]);
  const [chartWindow, setChartWindow] = useState<{ since: Date; until: Date } | null>(null);
  const [chartLoading, setChartLoading] = useState(false);
  const [chartError, setChartError] = useState<string | null>(null);

  const [pending, setPending] = useState<Record<string, Pending>>({});
  const [sending, setSending] = useState<Record<string, boolean>>({});
  const [dragBrightness, setDragBrightness] = useState<number | null>(null);
  const [commandError, setCommandError] = useState<string | null>(null);

  const readingCap = device ? pickReadingCapability(device.capabilities) : null;
  const chartCap = readingCap && isNumericCapability(readingCap) ? readingCap : null;

  useEffect(() => {
    if (activeEdgeId === null || !deviceId) return;
    const edgeId = activeEdgeId;
    const id = deviceId;
    let cancelled = false;
    let intervalId: number | undefined;

    async function pollState() {
      try {
        const next = await getDeviceState(edgeId, id);
        if (cancelled) return;
        setState(next);
        setPending((prev) => {
          const now = Date.now();
          const kept: Record<string, Pending> = {};
          for (const [cap, p] of Object.entries(prev)) {
            const current =
              cap === 'POWER_SWITCH'
                ? readSwitchValue(next)
                : findCapabilityState(next, cap as CapabilityType)?.value;
            const converged = current === p.value;
            const expired = now - p.sentAt > PENDING_TIMEOUT_MS;
            if (!converged && !expired) kept[cap] = p;
          }
          return kept;
        });
      } catch {
      }
    }

    setLoading(true);
    setError(null);
    getDevice(edgeId, id)
      .then((d) => {
        if (cancelled) return;
        setDevice(d);
        void pollState();
        intervalId = window.setInterval(() => void pollState(), POLL_INTERVAL_MS);
      })
      .catch((err) => {
        if (!cancelled) setError(extractError(err, 'No se pudo cargar el dispositivo.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
      if (intervalId !== undefined) window.clearInterval(intervalId);
    };
  }, [activeEdgeId, deviceId]);

  useEffect(() => {
    if (activeEdgeId === null || !deviceId || !chartCap) {
      setPoints([]);
      setChartWindow(null);
      return;
    }
    let cancelled = false;
    const until = new Date();
    const since = new Date(until.getTime() - TELEMETRY_WINDOW_HOURS * 3_600_000);
    setChartLoading(true);
    setChartError(null);
    setChartWindow({ since, until });
    getTelemetryAggregate(activeEdgeId, deviceId, {
      capability: chartCap,
      since: since.toISOString(),
      until: until.toISOString(),
      bucketMinutes: TELEMETRY_BUCKET_MINUTES,
    })
      .then((p) => {
        if (!cancelled) setPoints(p);
      })
      .catch((err) => {
        if (!cancelled) setChartError(extractError(err, 'No se pudo cargar la telemetría.'));
      })
      .finally(() => {
        if (!cancelled) setChartLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [activeEdgeId, deviceId, chartCap]);

  function issue(capability: CapabilityType, value: unknown, failMsg: string) {
    if (activeEdgeId === null || !deviceId) return;
    setPending((p) => ({ ...p, [capability]: { value, sentAt: Date.now() } }));
    setSending((s) => ({ ...s, [capability]: true }));
    sendCommand(activeEdgeId, deviceId, { capability, value })
      .catch(() => {
        setPending((p) => omit(p, capability));
        setCommandError(failMsg);
      })
      .finally(() => {
        setSending((s) => omit(s, capability));
      });
  }

  function handleToggle(nextOn: boolean) {
    issue('POWER_SWITCH', nextOn, `No se pudo enviar el comando a "${device?.name}".`);
  }

  function handleBrightnessCommit(value: number) {
    setDragBrightness(null);
    issue('BRIGHTNESS', value, `No se pudo ajustar el brillo de "${device?.name}".`);
  }

  const hasSwitch = device?.capabilities.includes('POWER_SWITCH') ?? false;
  const hasBrightness = device?.capabilities.includes('BRIGHTNESS') ?? false;

  const switchOn = pending['POWER_SWITCH'] ? Boolean(pending['POWER_SWITCH'].value) : readSwitchValue(state);
  const switchBusy = sending['POWER_SWITCH'] === true;

  const stateBrightness = Number(findCapabilityState(state, 'BRIGHTNESS')?.value ?? 0);
  const pendingBrightness = pending['BRIGHTNESS'] ? Number(pending['BRIGHTNESS'].value) : null;
  const brightnessValue = dragBrightness ?? pendingBrightness ?? stateBrightness;
  const brightnessBusy = sending['BRIGHTNESS'] === true;

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
        <IconButton onClick={() => navigate(-1)} aria-label="Volver">
          <ArrowBackIcon />
        </IconButton>
        <Typography variant="h4">{device ? device.name : 'Dispositivo'}</Typography>
      </Box>

      {activeEdgeId === null ? (
        <Alert severity="info">No hay ninguna casa activa.</Alert>
      ) : loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
          <CircularProgress />
        </Box>
      ) : error ? (
        <Alert severity="error">{error}</Alert>
      ) : device ? (
        <Stack spacing={2}>
          <Card sx={{ borderRadius: 4 }}>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <Box sx={{ color: 'text.secondary', display: 'flex' }}>{TYPE_ICON[device.type]}</Box>
                <Typography variant="h6" sx={{ flexGrow: 1, minWidth: 0 }} noWrap>
                  {device.name}
                </Typography>
                {canManageDevices && (
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    <Button size="small" startIcon={<EditIcon />} onClick={() => setDialog('edit')}>
                      Editar nombre
                    </Button>
                    <Button
                      size="small"
                      color="error"
                      startIcon={<DeleteOutlineIcon />}
                      onClick={() => setDialog('delete')}
                    >
                      Eliminar
                    </Button>
                  </Box>
                )}
              </Box>
              <Stack spacing={1.5}>
                {edgeApi && <CopyField label="ID del dispositivo" value={device.id} />}
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">
                    Tipo
                  </Typography>
                  <Typography variant="body2">{DEVICE_TYPE_LABEL[device.type]}</Typography>
                </Box>
                <Box>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    Capabilities
                  </Typography>
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                    {device.capabilities.map((c) => (
                      <Chip key={c} label={CAPABILITY_LABEL[c]} size="small" />
                    ))}
                  </Box>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">
                    Creado
                  </Typography>
                  <Typography variant="body2">{formatDate(device.createdAt)}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Typography variant="body2" color="text.secondary">
                    Actualizado
                  </Typography>
                  <Typography variant="body2">{formatDate(device.updatedAt)}</Typography>
                </Box>
                {device.lastSyncedAt && (
                  <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                    <Typography variant="body2" color="text.secondary">
                      Última sincronización
                    </Typography>
                    <Typography variant="body2">{formatDate(device.lastSyncedAt)}</Typography>
                  </Box>
                )}
              </Stack>
            </CardContent>
          </Card>

          {(hasSwitch || hasBrightness) && (
            <Card sx={{ borderRadius: 4 }}>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Controles
                </Typography>
                {hasSwitch && (
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <Typography variant="body2" color="text.secondary">
                      {switchOn ? 'Encendido' : 'Apagado'}
                    </Typography>
                    <Switch
                      color="success"
                      checked={switchOn}
                      disabled={switchBusy}
                      onChange={(e) => handleToggle(e.target.checked)}
                      slotProps={{ input: { 'aria-label': `Encender o apagar ${device.name}` } }}
                    />
                  </Box>
                )}
                {hasBrightness && (
                  <Box sx={{ mt: hasSwitch ? 2 : 0 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                      <Typography variant="body2" color="text.secondary">
                        Brillo
                      </Typography>
                      <Typography variant="body2">{Math.round(brightnessValue)} %</Typography>
                    </Box>
                    <Slider
                      value={brightnessValue}
                      min={0}
                      max={100}
                      disabled={brightnessBusy}
                      onChange={(_, v) => setDragBrightness(v as number)}
                      onChangeCommitted={(_, v) => handleBrightnessCommit(v as number)}
                      valueLabelDisplay="auto"
                      aria-label={`Brillo de ${device.name}`}
                    />
                  </Box>
                )}
              </CardContent>
            </Card>
          )}

          {readingCap && (
            <Card sx={{ borderRadius: 4 }}>
              <CardContent>
                {chartCap ? (
                  <>
                    <Typography variant="h6" gutterBottom>
                      {CAPABILITY_LABEL[chartCap]} · {TELEMETRY_WINDOW_LABEL}
                    </Typography>
                    {chartLoading ? (
                      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                        <CircularProgress />
                      </Box>
                    ) : chartError ? (
                      <Alert severity="error">{chartError}</Alert>
                    ) : points.length === 0 || chartWindow === null ? (
                      <Typography variant="body2" color="text.secondary">
                        Sin datos de telemetría todavía
                      </Typography>
                    ) : (
                      <TelemetryChart
                        points={points}
                        since={chartWindow.since}
                        until={chartWindow.until}
                        bucketMinutes={TELEMETRY_BUCKET_MINUTES}
                        label={CAPABILITY_LABEL[chartCap]}
                        unit={CAPABILITY_META[chartCap].unit}
                        yMin={CAPABILITY_META[chartCap].min}
                        yMax={CAPABILITY_META[chartCap].max}
                      />
                    )}
                  </>
                ) : (
                  <>
                    <Typography variant="h6" gutterBottom>
                      {CAPABILITY_LABEL[readingCap]}
                    </Typography>
                    <Typography variant="h4">
                      {formatReading(readingCap, findCapabilityState(state, readingCap)?.value)}
                    </Typography>
                  </>
                )}
              </CardContent>
            </Card>
          )}
        </Stack>
      ) : null}

      {canManageDevices && device && activeEdgeId && (
        <>
          <EditDeviceNameDialog
            open={dialog === 'edit'}
            edgeId={activeEdgeId}
            device={device}
            onClose={() => setDialog(null)}
            onSaved={setDevice}
          />
          <DeleteDeviceDialog
            open={dialog === 'delete'}
            edgeId={activeEdgeId}
            device={device}
            onClose={() => setDialog(null)}
            onDeleted={() => navigate('/devices', { replace: true })}
          />
        </>
      )}

      <Snackbar
        open={commandError !== null}
        autoHideDuration={4000}
        onClose={() => setCommandError(null)}
      >
        <Alert severity="error" onClose={() => setCommandError(null)} sx={{ width: '100%' }}>
          {commandError}
        </Alert>
      </Snackbar>
    </Box>
  );
}

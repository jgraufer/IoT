import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Card,
  CardContent,
  CircularProgress,
  Link,
  Snackbar,
  Typography,
} from "@mui/material";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import type { CapabilityType, TelemetryAggregatePoint } from "../types/api";
import { useEdges } from "../session/EdgeProvider";
import { getTelemetryAverage } from "../devices/devicesApi";
import { CAPABILITY_LABEL, CAPABILITY_META } from "./deviceDisplay";
import { DeviceRow } from "./DeviceRow";
import { useDeviceStates } from "./useDeviceStates";
import { TelemetryChart } from "../devices/TelemetryChart";
import {
  TELEMETRY_BUCKET_MINUTES,
  TELEMETRY_WINDOW_HOURS,
  TELEMETRY_WINDOW_LABEL,
} from "../shared/telemetryWindow";

const AGG_CAPS: CapabilityType[] = ["TEMPERATURE", "HUMIDITY"];

interface ChartData {
  points: TelemetryAggregatePoint[];
  loading: boolean;
  error: string | null;
  since: Date;
  until: Date;
}

function extractError(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const msg = (err.response?.data as { message?: string } | undefined)
      ?.message;
    if (msg) return msg;
  }
  return fallback;
}

function AverageChartCard({
  capability,
  chart,
}: {
  capability: CapabilityType;
  chart: ChartData;
}) {
  return (
    <Card sx={{ borderRadius: 4 }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          {CAPABILITY_LABEL[capability]} · media · {TELEMETRY_WINDOW_LABEL}
        </Typography>
        {chart.loading ? (
          <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
            <CircularProgress />
          </Box>
        ) : chart.error ? (
          <Alert severity="error">{chart.error}</Alert>
        ) : chart.points.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            Sin datos de telemetría todavía
          </Typography>
        ) : (
          <TelemetryChart
            points={chart.points}
            since={chart.since}
            until={chart.until}
            bucketMinutes={TELEMETRY_BUCKET_MINUTES}
            label={CAPABILITY_LABEL[capability]}
            unit={CAPABILITY_META[capability].unit}
            yMin={CAPABILITY_META[capability].min}
            yMax={CAPABILITY_META[capability].max}
          />
        )}
      </CardContent>
    </Card>
  );
}

export function DashboardPage() {
  const { activeEdgeId } = useEdges();
  const navigate = useNavigate();
  const {
    devices,
    states,
    loading,
    error,
    commandError,
    clearCommandError,
    switchValue,
    switchBusy,
    toggle,
  } = useDeviceStates(activeEdgeId);
  const [charts, setCharts] = useState<
    Partial<Record<CapabilityType, ChartData>>
  >({});

  const hasTemperature = useMemo(
    () => devices.some((d) => d.capabilities.includes("TEMPERATURE")),
    [devices],
  );
  const hasHumidity = useMemo(
    () => devices.some((d) => d.capabilities.includes("HUMIDITY")),
    [devices],
  );

  useEffect(() => {
    if (activeEdgeId === null) {
      setCharts({});
      return;
    }
    const present = AGG_CAPS.filter(
      (c) =>
        (c === "TEMPERATURE" && hasTemperature) ||
        (c === "HUMIDITY" && hasHumidity),
    );
    if (present.length === 0) {
      setCharts({});
      return;
    }
    const edgeId = activeEdgeId;
    let cancelled = false;
    const until = new Date();
    const since = new Date(until.getTime() - TELEMETRY_WINDOW_HOURS * 3_600_000);

    setCharts(
      Object.fromEntries(
        present.map((cap) => [
          cap,
          { points: [], loading: true, error: null, since, until },
        ]),
      ),
    );

    for (const cap of present) {
      getTelemetryAverage(edgeId, {
        capability: cap,
        since: since.toISOString(),
        until: until.toISOString(),
        bucketMinutes: TELEMETRY_BUCKET_MINUTES,
      })
        .then((points) => {
          if (!cancelled) {
            setCharts((prev) => ({
              ...prev,
              [cap]: { since, until, error: null, loading: false, points },
            }));
          }
        })
        .catch((err) => {
          if (!cancelled) {
            const msg = extractError(err, "No se pudo cargar la telemetría.");
            setCharts((prev) => ({
              ...prev,
              [cap]: { since, until, points: [], loading: false, error: msg },
            }));
          }
        });
    }

    return () => {
      cancelled = true;
    };
  }, [activeEdgeId, hasTemperature, hasHumidity]);

  const total = devices.length;
  const onCount = devices.filter((d) => {
    if (!d.capabilities.includes("POWER_SWITCH")) return true;
    return switchValue(d.id);
  }).length;

  const presentCharts = AGG_CAPS.filter((c) => charts[c] !== undefined);

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Dashboard
      </Typography>

      {loading ? (
        <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
          <CircularProgress />
        </Box>
      ) : error ? (
        <Alert severity="error">{error}</Alert>
      ) : devices.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          Esta casa no tiene dispositivos todavía.
        </Typography>
      ) : (
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
            <Card sx={{ borderRadius: 4, flex: "1 1 160px" }}>
              <CardContent>
                <Typography variant="h3">
                  {onCount}
                  <Typography
                    component="span"
                    variant="h5"
                    color="text.secondary"
                  >
                    {" "}
                    / {total}
                  </Typography>
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Encendidos
                </Typography>
              </CardContent>
            </Card>
            <Card sx={{ borderRadius: 4, flex: "1 1 160px" }}>
              <CardContent>
                <Typography variant="h3">{total}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Dispositivos
                </Typography>
              </CardContent>
            </Card>
          </Box>

          {presentCharts.length > 0 && (
            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
                gap: 2,
              }}
            >
              {presentCharts.map((cap) => (
                <AverageChartCard
                  key={cap}
                  capability={cap}
                  chart={charts[cap]!}
                />
              ))}
            </Box>
          )}

          <Card sx={{ borderRadius: 4, gap: 1, width: "calc(50% - 8px)" }}>
            <CardContent sx={{ p: 1, ":last-child": { pb: 1 } }}>
              {devices.slice(0, 6).map((device) => (
                <DeviceRow
                  key={device.id}
                  device={device}
                  state={states[device.id]}
                  switchOn={switchValue(device.id)}
                  switchBusy={switchBusy(device.id)}
                  onToggle={(next) => toggle(device, next)}
                  onOpen={() => navigate(`/devices/${device.id}`)}
                />
              ))}
              {devices.length > 6 && (
                <Box sx={{ px: 2, py: 1 }}>
                  <Link
                    component="button"
                    type="button"
                    onClick={() => navigate("/devices")}
                  >
                    Ver todos
                  </Link>
                </Box>
              )}
            </CardContent>
          </Card>
        </Box>
      )}

      <Snackbar
        open={commandError !== null}
        autoHideDuration={4000}
        onClose={clearCommandError}
      >
        <Alert
          severity="error"
          onClose={clearCommandError}
          sx={{ width: "100%" }}
        >
          {commandError}
        </Alert>
      </Snackbar>
    </Box>
  );
}

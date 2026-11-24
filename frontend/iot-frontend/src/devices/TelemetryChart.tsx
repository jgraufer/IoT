import { useTheme } from '@mui/material';
import { LineChart } from '@mui/x-charts/LineChart';
import type { TelemetryAggregatePoint } from '../types/api';

interface TelemetryChartProps {
  points: TelemetryAggregatePoint[];
  since: Date;
  until: Date;
  bucketMinutes: number;
  label: string;
  unit?: string;
  yMin?: number;
  yMax?: number;
}

const GAP_FACTOR = 1.5;

export function TelemetryChart({
  points,
  since,
  until,
  bucketMinutes,
  label,
  unit,
  yMin,
  yMax,
}: TelemetryChartProps) {
  const theme = useTheme();
  const bucketMs = bucketMinutes * 60_000;

  const xValues: Date[] = [];
  const yValues: (number | null)[] = [];
  let prevMs: number | null = null;
  for (const p of points) {
    const ms = new Date(p.timestamp).getTime();
    if (prevMs !== null && ms - prevMs > bucketMs * GAP_FACTOR) {
      xValues.push(new Date(prevMs + bucketMs));
      yValues.push(null);
    }
    xValues.push(new Date(ms));
    yValues.push(p.average);
    prevMs = ms;
  }

  const formatValue = (v: number | null) =>
    v == null ? '' : unit ? `${v.toFixed(1)} ${unit}` : v.toFixed(1);

  let axisMin = yMin;
  let axisMax = yMax;
  if (axisMin === undefined || axisMax === undefined) {
    const nums = yValues.filter((v): v is number => v !== null);
    if (nums.length > 0) {
      const dataMin = Math.min(...nums);
      const dataMax = Math.max(...nums);
      const span = dataMax - dataMin;
      const pad = span > 0 ? span * 0.15 : 1;
      axisMin = axisMin ?? dataMin - pad;
      axisMax = axisMax ?? dataMax + pad;
    }
  }

  const formatAxis = (value: number) => (unit ? `${value} ${unit}` : `${value}`);

  return (
    <LineChart
      height={320}
      hideLegend
      xAxis={[
        {
          data: xValues,
          scaleType: 'time',
          min: since,
          max: until,
          valueFormatter: (value) => {
            const d = value instanceof Date ? value : new Date(value as number);
            return d.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
          },
        },
      ]}
      yAxis={[{ min: axisMin, max: axisMax, valueFormatter: formatAxis }]}
      series={[
        {
          data: yValues,
          label,
          showMark: false,
          connectNulls: false,
          curve: 'linear',
          color: theme.palette.primary.main,
          valueFormatter: formatValue,
        },
      ]}
    />
  );
}

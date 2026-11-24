import { Box, Chip, Switch, TableCell, TableRow, Typography } from "@mui/material";
import type { Device, DeviceState } from "../types/api";
import {
  CAPABILITY_LABEL,
  DEVICE_TYPE_LABEL,
  findCapabilityState,
  formatReading,
  pickReadingCapability,
} from "../dashboard/deviceDisplay";
import { TYPE_ICON } from "../shared/deviceIcons";

interface DeviceTableRowProps {
  device: Device;
  state?: DeviceState;
  switchOn: boolean;
  switchBusy: boolean;
  onToggle: (next: boolean) => void;
  onOpen: () => void;
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleDateString("es-ES", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

export function DeviceTableRow({
  device,
  state,
  switchOn,
  switchBusy,
  onToggle,
  onOpen,
}: DeviceTableRowProps) {
  const hasSwitch = device.capabilities.includes("POWER_SWITCH");
  const readingCap = pickReadingCapability(device.capabilities);
  const readingState = readingCap ? findCapabilityState(state, readingCap) : undefined;

  return (
    <TableRow hover onClick={onOpen} sx={{ cursor: "pointer" }}>
      <TableCell>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <Box sx={{ color: "text.secondary", display: "flex" }}>{TYPE_ICON[device.type]}</Box>
          {device.name}
        </Box>
      </TableCell>

      <TableCell>{DEVICE_TYPE_LABEL[device.type]}</TableCell>

      <TableCell>
        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5 }}>
          {device.capabilities.map((c) => (
            <Chip key={c} label={CAPABILITY_LABEL[c]} size="small" />
          ))}
        </Box>
      </TableCell>

      <TableCell>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          {readingCap && (
            <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: "nowrap" }}>
              {readingState ? formatReading(readingCap, readingState.value) : "—"}
            </Typography>
          )}
          {hasSwitch && (
            <Box onClick={(e) => e.stopPropagation()} sx={{ display: "flex" }}>
              <Switch
                color="success"
                checked={switchOn}
                disabled={switchBusy}
                onChange={(e) => onToggle(e.target.checked)}
                slotProps={{ input: { "aria-label": `Encender o apagar ${device.name}` } }}
              />
            </Box>
          )}
        </Box>
      </TableCell>

      <TableCell sx={{ whiteSpace: "nowrap", color: "text.secondary" }}>
        {formatDate(device.updatedAt)}
      </TableCell>
    </TableRow>
  );
}

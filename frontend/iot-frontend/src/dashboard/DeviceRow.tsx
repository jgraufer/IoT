import { Box, Switch, Typography } from "@mui/material";
import type { Device, DeviceState } from "../types/api";
import { TYPE_ICON } from "../shared/deviceIcons";
import {
  findCapabilityState,
  formatReading,
  pickReadingCapability,
} from "./deviceDisplay";

interface DeviceRowProps {
  device: Device;
  state?: DeviceState;
  switchOn: boolean;
  switchBusy: boolean;
  onToggle: (next: boolean) => void;
  onOpen: () => void;
}

export function DeviceRow({
  device,
  state,
  switchOn,
  switchBusy,
  onToggle,
  onOpen,
}: DeviceRowProps) {
  const hasSwitch = device.capabilities.includes("POWER_SWITCH");
  const readingCap = pickReadingCapability(device.capabilities);
  const readingState = readingCap ? findCapabilityState(state, readingCap) : undefined;

  return (
    <Box
      onClick={onOpen}
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 1.5,
        px: 2,
        py: 1.5,
        cursor: "pointer",
        borderRadius: 2,
        transition: "background-color 120ms",
        ":hover": { bgcolor: "action.hover" },
      }}
    >
      <Box sx={{ color: "text.secondary", display: "flex" }}>{TYPE_ICON[device.type]}</Box>
      <Typography noWrap sx={{ flex: 1, minWidth: 0 }}>
        {device.name}
      </Typography>

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
  );
}

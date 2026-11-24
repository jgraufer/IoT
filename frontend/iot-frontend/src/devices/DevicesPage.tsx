import { useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Paper,
  Snackbar,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import { useNavigate } from "react-router-dom";
import type { DeviceType } from "../types/api";
import { useEdges } from "../session/EdgeProvider";
import { useDeviceStates } from "../dashboard/useDeviceStates";
import { DEVICE_TYPE_LABEL } from "../dashboard/deviceDisplay";
import { DeviceTableRow } from "./DeviceTableRow";
import { CreateDeviceDialog } from "./CreateDeviceDialog";
import { useAuth } from "../auth/AuthContext";

const TYPE_ORDER: DeviceType[] = ["LIGHT", "SENSOR", "THERMOSTAT", "OUTLET", "OTHER"];
type Filter = DeviceType | "all";

export function DevicesPage() {
  const { activeEdgeId } = useEdges();
  const navigate = useNavigate();
  const { canManageDevices } = useAuth();
  const [creating, setCreating] = useState(false);
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
    reload,
  } = useDeviceStates(activeEdgeId);
  const [filter, setFilter] = useState<Filter>("all");

  const presentTypes = useMemo(
    () => TYPE_ORDER.filter((type) => devices.some((d) => d.type === type)),
    [devices],
  );

  const activeFilter: Filter = filter !== "all" && !presentTypes.includes(filter) ? "all" : filter;
  const shown = activeFilter === "all" ? devices : devices.filter((d) => d.type === activeFilter);

  return (
    <Box>
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2, mb: 1 }}>
        <Typography variant="h4">Dispositivos</Typography>
        {canManageDevices && activeEdgeId && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>
            Añadir dispositivo
          </Button>
        )}
      </Box>

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
          <Tabs
            value={activeFilter}
            onChange={(_, v: Filter) => setFilter(v)}
            variant="scrollable"
            scrollButtons="auto"
          >
            <Tab value="all" label="Todos" />
            {presentTypes.map((type) => (
              <Tab key={type} value={type} label={DEVICE_TYPE_LABEL[type]} />
            ))}
          </Tabs>

          <TableContainer component={Paper} sx={{ borderRadius: 3 }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Dispositivo</TableCell>
                  <TableCell>Tipo</TableCell>
                  <TableCell>Capacidades</TableCell>
                  <TableCell>Estado / lectura</TableCell>
                  <TableCell>Actualizado</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {shown.map((device) => (
                  <DeviceTableRow
                    key={device.id}
                    device={device}
                    state={states[device.id]}
                    switchOn={switchValue(device.id)}
                    switchBusy={switchBusy(device.id)}
                    onToggle={(next) => toggle(device, next)}
                    onOpen={() => navigate(`/devices/${device.id}`)}
                  />
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
      )}

      {canManageDevices && activeEdgeId && (
        <CreateDeviceDialog
          open={creating}
          edgeId={activeEdgeId}
          onClose={() => setCreating(false)}
          onCreated={reload}
        />
      )}

      <Snackbar
        open={commandError !== null}
        autoHideDuration={4000}
        onClose={clearCommandError}
      >
        <Alert severity="error" onClose={clearCommandError} sx={{ width: "100%" }}>
          {commandError}
        </Alert>
      </Snackbar>
    </Box>
  );
}
